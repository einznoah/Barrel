#!/usr/bin/env python3
"""Regenerates src/main/resources/runtime_items.json and bedrock_item_tags.json.

Run this whenever the Java or Bedrock version Barrel targets changes. The first two inputs are
in https://github.com/GeyserMC/mappings (use the commit for that Java version):

  --items       items.json (Java item -> Bedrock item)
  --components  item_data_components.json (Java item ids and their default components)
  --item-tags   item_tags.<bedrock version>.json from https://github.com/GeyserMC/Geyser
                (core/src/main/resources/bedrock), the items of the tags recipes ask for

Bedrock item runtime ids are not in the output, every server sends its own.
"""
import argparse
import base64
import collections
import json
import os
import struct

# Ids of the max_stack_size, max_damage and consumable data components, the first byte of their encoded form
MAX_STACK_SIZE_COMPONENT = 1
MAX_DAMAGE_COMPONENT = 2
REPAIRABLE_COMPONENT = 33
CONSUMABLE_COMPONENT = 24


def read_var_int(data, offset):
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value
        shift += 7


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--items', required=True)
    parser.add_argument('--components', required=True)
    parser.add_argument('--item-tags', required=True)
    parser.add_argument('--output', default=os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources'))
    args = parser.parse_args()

    with open(args.items) as file:
        mappings = json.load(file)
    with open(args.components) as file:
        java_items = json.load(file)

    # Several Java items can be one Bedrock item, told apart by its data value (beds, banners)
    shared = collections.Counter(mapping['bedrock_identifier'] for mapping in mappings.values())

    runtime_items = {}
    for java_item in sorted(java_items, key=lambda item: item['id']):
        mapping = mappings.get(java_item['key'])
        if mapping is None:
            raise SystemExit('No Bedrock mapping for %s, are both files for the same Java version?' % java_item['key'])

        entry = {'java_name': java_item['key'], 'bedrock_name': mapping['bedrock_identifier']}
        if shared[mapping['bedrock_identifier']] > 1:
            entry['bedrock_data'] = mapping.get('bedrock_data', 0)

        max_stack_size = base64.b64decode(java_item['components']['minecraft:max_stack_size'])
        if max_stack_size[0] != MAX_STACK_SIZE_COMPONENT:
            raise SystemExit('The data component ids changed, max_stack_size is no longer %d' % MAX_STACK_SIZE_COMPONENT)
        if read_var_int(max_stack_size, 1) != 64:
            entry['max_stack_size'] = read_var_int(max_stack_size, 1)

        # How much damage a tool or a piece of armor takes before it breaks, an anvil mends a quarter of it at a time
        if 'minecraft:max_damage' in java_item['components']:
            max_damage = base64.b64decode(java_item['components']['minecraft:max_damage'])
            if max_damage[0] != MAX_DAMAGE_COMPONENT:
                raise SystemExit('The data component ids changed, max_damage is no longer %d' % MAX_DAMAGE_COMPONENT)
            entry['max_damage'] = read_var_int(max_damage, 1)

        # What an anvil mends the item with, a tag of items or the ids of the items themselves
        if 'minecraft:repairable' in java_item['components']:
            repairable = base64.b64decode(java_item['components']['minecraft:repairable'])
            if repairable[0] != REPAIRABLE_COMPONENT:
                raise SystemExit('The data component ids changed, repairable is no longer %d' % REPAIRABLE_COMPONENT)
            count = read_var_int(repairable, 1)
            if count == 0:
                entry['repair_tag'] = repairable[3:3 + repairable[2]].decode()
            else:
                repair_items = []
                offset = 2
                for _ in range(count - 1):
                    repair_items.append(read_var_int(repairable, offset))
                    while repairable[offset] & 0x80:
                        offset += 1
                    offset += 1
                entry['repair_items'] = repair_items

        # What is eaten or drunk, and how long that takes. A Bedrock server is told when the player is done
        if 'minecraft:consumable' in java_item['components']:
            consumable = base64.b64decode(java_item['components']['minecraft:consumable'])
            if consumable[0] != CONSUMABLE_COMPONENT:
                raise SystemExit('The data component ids changed, consumable is no longer %d' % CONSUMABLE_COMPONENT)
            entry['consume_ticks'] = round(struct.unpack('>f', consumable[1:5])[0] * 20)

        runtime_items[str(java_item['id'])] = entry

    with open(os.path.join(args.output, 'runtime_items.json'), 'w') as file:
        json.dump(runtime_items, file, indent=2)

    with open(args.item_tags) as file:
        item_tags = json.load(file)
    with open(os.path.join(args.output, 'bedrock_item_tags.json'), 'w') as file:
        json.dump({tag: sorted(items) for tag, items in sorted(item_tags.items())}, file, indent=2)

    print('%d Java items mapped, %d Bedrock item tags' % (len(runtime_items), len(item_tags)))


if __name__ == '__main__':
    main()
