#!/usr/bin/env python3
"""Regenerates src/main/resources/runtime_blocks.json and unknown_blocks.json.

Run this whenever the Java or Bedrock version Barrel targets changes. Inputs:

  --java-states      mapping-<java version>.json from https://github.com/ViaVersion/Mappings
                     (its "blockstates" array is indexed by Java block state id)
  --java-defaults    blocks/data.min.json from the <java version>-summary tag of
                     https://github.com/misode/mcmeta (default properties of every block)
  --bedrock-mappings blocks.nbt from https://github.com/GeyserMC/mappings
                     (Java block state id -> Bedrock block state)
  --bedrock-palette  block_palette.<bedrock version>.nbt from
                     https://github.com/GeyserMC/Geyser (core/src/main/resources/bedrock);
                     its order is the Bedrock runtime id

The mappings and the Java state list must be for the same Java version, otherwise the
script aborts.
"""
import argparse
import gzip
import json
import os
import struct

# Bedrock has two blocks per liquid. The mappings only use the still one for the source block
# and the flowing one for every other level, servers send both with any level.
BEDROCK_ALIASES = {
    'minecraft:water': 'minecraft:flowing_water',
    'minecraft:flowing_water': 'minecraft:water',
    'minecraft:lava': 'minecraft:flowing_lava',
    'minecraft:flowing_lava': 'minecraft:lava',
}

# Java blocks that hold a fluid without having a waterlogged property
JAVA_FLUID_BLOCKS = {'water', 'lava', 'bubble_column', 'kelp', 'kelp_plant', 'seagrass', 'tall_seagrass'}


class NbtReader:
    """Just enough big-endian NBT to read Geyser's mapping files."""

    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        value = struct.unpack_from(fmt, self.data, self.pos)[0]
        self.pos += struct.calcsize(fmt)
        return value

    def string(self):
        length = self.take('>H')
        value = self.data[self.pos:self.pos + length].decode('utf-8')
        self.pos += length
        return value

    def payload(self, tag):
        if tag == 1:
            return self.take('>b')
        if tag == 2:
            return self.take('>h')
        if tag == 3:
            return self.take('>i')
        if tag == 4:
            return self.take('>q')
        if tag == 5:
            return self.take('>f')
        if tag == 6:
            return self.take('>d')
        if tag == 7:
            return [self.take('>b') for _ in range(self.take('>i'))]
        if tag == 8:
            return self.string()
        if tag == 9:
            element_tag = self.take('>b')
            return [self.payload(element_tag) for _ in range(self.take('>i'))]
        if tag == 10:
            compound = {}
            while True:
                element_tag = self.take('>b')
                if element_tag == 0:
                    return compound
                # Keep the tag id, a byte 1 and an int 1 are different block states
                name = self.string()
                compound[name] = (element_tag, self.payload(element_tag))
        if tag == 11:
            return [self.take('>i') for _ in range(self.take('>i'))]
        if tag == 12:
            return [self.take('>q') for _ in range(self.take('>i'))]
        raise ValueError('Unknown NBT tag %d' % tag)


def read_nbt(path):
    with open(path, 'rb') as file:
        data = file.read()
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    reader = NbtReader(data)
    tag = reader.take('>b')
    reader.string()
    return reader.payload(tag)


def state_key(name, states):
    return name, tuple(sorted(states.items()))


def parse_java_state(state):
    name, _, properties = state.partition('[')
    if not properties:
        return name, {}
    return name, dict(pair.split('=') for pair in properties[:-1].split(','))


def format_java_state(name, properties):
    if not properties:
        return name
    return name + '[' + ','.join(key + '=' + value for key, value in properties.items()) + ']'


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--java-states', required=True)
    parser.add_argument('--java-defaults', required=True)
    parser.add_argument('--bedrock-mappings', required=True)
    parser.add_argument('--bedrock-palette', required=True)
    parser.add_argument('--output', default=os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources'))
    args = parser.parse_args()

    with open(args.java_states) as file:
        java_states = json.load(file)['blockstates']
    with open(args.java_defaults) as file:
        java_defaults = json.load(file)
    mappings = read_nbt(args.bedrock_mappings)['bedrock_mappings'][1]
    palette = read_nbt(args.bedrock_palette)['blocks'][1]

    if len(java_states) != len(mappings):
        raise SystemExit('The Java block states (%d) and the Bedrock mappings (%d) are for different Java versions'
                         % (len(java_states), len(mappings)))

    java_state_ids = {state: state_id for state_id, state in enumerate(java_states)}
    runtime_ids = {}
    for runtime_id, block in enumerate(palette):
        runtime_ids[state_key(block['name'][1], block['states'][1])] = runtime_id

    # Every Java state that turns into a given Bedrock state
    candidates = {}
    for java_id, mapping in enumerate(mappings):
        java_name, _ = parse_java_state(java_states[java_id])
        bedrock_name = 'minecraft:' + mapping['bedrock_identifier'][1] if 'bedrock_identifier' in mapping else 'minecraft:' + java_name
        states = mapping['state'][1] if 'state' in mapping else {}
        runtime_id = runtime_ids.get(state_key(bedrock_name, states))
        if runtime_id is None:
            raise SystemExit('The Bedrock palette has no %s %s (Java %s), is it for another Bedrock version?'
                             % (bedrock_name, states, java_states[java_id]))
        candidates.setdefault(runtime_id, []).append(java_id)

    def preference(java_id):
        # Several Java states share one Bedrock state when Bedrock lacks a property (snowy grass,
        # note block pitch, ...). Take the one closest to the block's default state, but never a
        # waterlogged one: Bedrock keeps the water in a second layer.
        name, properties = parse_java_state(java_states[java_id])
        defaults = java_defaults[name][1] if name in java_defaults and len(java_defaults[name]) > 1 else {}
        mismatches = sum(1 for key, value in properties.items() if key != 'waterlogged' and defaults.get(key) != value)
        return properties.get('waterlogged') == 'true', mismatches, java_id

    java_ids = {runtime_id: min(states, key=preference) for runtime_id, states in candidates.items()}

    # Java never produces some Bedrock states (leaves with update_bit set, infiniburn bedrock, ...)
    # but Bedrock servers do send them. Show those as the most similar state of the same block.
    siblings = {}
    for runtime_id in sorted(java_ids):
        siblings.setdefault(palette[runtime_id]['name'][1], []).append(runtime_id)

    known = []
    unknown = {}
    for runtime_id, block in enumerate(palette):
        if runtime_id not in java_ids:
            states = block['states'][1]
            similar = runtime_ids.get(state_key(BEDROCK_ALIASES.get(block['name'][1]), states))
            if similar not in java_ids:
                similar = max(siblings.get(block['name'][1], []), default=None, key=lambda sibling: sum(
                    1 for key, value in palette[sibling]['states'][1].items() if states.get(key) == value))
            if similar is None:
                unknown[str(runtime_id)] = {'bedrock_name': block['name'][1], 'java_default_state': '??'}
                continue
            java_ids[runtime_id] = java_ids[similar]

        java_id = java_ids[runtime_id]
        entry = {
            'bedrock_name': block['name'][1],
            'bedrock_network_id': block['network_id'][1],
            'java_default_state': java_id,
        }

        name, properties = parse_java_state(java_states[java_id])
        if name in JAVA_FLUID_BLOCKS or properties.get('waterlogged') == 'true':
            entry['java_fluid'] = True
        if properties.get('waterlogged') == 'false':
            properties['waterlogged'] = 'true'
            entry['java_waterlogged_state'] = java_state_ids[format_java_state(name, properties)]
        known.append((java_id, runtime_id, entry))

    known.sort()
    runtime_blocks = {str(runtime_id): entry for _, runtime_id, entry in known}

    with open(os.path.join(args.output, 'runtime_blocks.json'), 'w') as file:
        json.dump(runtime_blocks, file, indent=2)
    with open(os.path.join(args.output, 'unknown_blocks.json'), 'w') as file:
        json.dump(unknown, file, indent=4)

    print('%d Bedrock block states mapped, %d without a Java counterpart' % (len(runtime_blocks), len(unknown)))


if __name__ == '__main__':
    main()
