#!/usr/bin/env python3
"""Regenerates src/main/resources/sounds.json: the Java sound for what a Bedrock server tells of a sound.

Run this whenever the Java or Bedrock version Barrel targets changes.

  --sounds  sounds.json of https://github.com/GeyserMC/mappings (use the commit for that Java version). It lists,
            for every Java sound, how a Bedrock client is told the same sound. This turns it around.
  --blocks  blocks.json of the vanilla resource pack that comes with the Bedrock Dedicated Server
            (resource_packs/vanilla/blocks.json). It names the kind of sound of the older blocks. Optional: without
            it, and for every block it does not list, the kind of sound is guessed from the name of the block.

The names of the Bedrock blocks are read from runtime_blocks.json, which is next to the output.

The output has three parts:
  events      what a Bedrock server tells by a sound event: "EVENT", "EVENT|<java entity>" for the sound of an
              entity, "EVENT|<bedrock identifier>" and "EVENT|<number>" where a server tells more with the event
  playsounds  what a Bedrock server tells by the name of a sound
  blocks      the kind of sound of a block, the part between "block." and ".break" of the Java sounds. Blocks that
              sound like stone are left out
"""
import argparse
import collections
import json
import os
import re

# The sound events a server tells for a block, with the block: which Java sound that is depends on the block
BLOCK_EVENTS = {'STEP', 'HIT', 'PLACE', 'BREAK', 'BREAK_BLOCK', 'FALL', 'LAND', 'JUMP', 'HEAVY_STEP', 'ITEM_USE_ON'}

# What the list of Java sounds gets wrong when it is turned around: a Bedrock sound that several Java sounds are
# told as, where the one with the plainest name is not the common one, and sounds it does not tell at all. None
# for a Bedrock sound a Java client has nothing for
EVENTS = {
    'POP': 'entity.item.pickup', 'SPAWN': None, 'TELEPORT': 'entity.enderman.teleport', 'THROW': 'entity.snowball.throw', 'BOW': 'entity.arrow.shoot',
    'BOW_HIT': 'entity.arrow.hit', 'DRINK': 'entity.generic.drink', 'FIZZ': 'block.fire.extinguish', 'THUNDER': 'entity.lightning_bolt.thunder',
}
PLAYSOUNDS = {
    'random.orb': 'entity.experience_orb.pickup', 'random.pop': 'entity.item.pickup', 'random.bow': 'entity.arrow.shoot', 'random.bowhit': 'entity.arrow.hit',
    'random.door_open': 'block.wooden_door.open', 'random.door_close': 'block.wooden_door.close', 'random.click': 'block.lever.click',
    'random.fizz': 'block.fire.extinguish', 'random.break': 'entity.item.break',
}

# The Java kind of sound for the kinds a Bedrock resource pack names
BEDROCK_KINDS = {
    'stone': 'stone', 'wood': 'wood', 'grass': 'grass', 'metal': 'metal', 'glass': 'glass', 'gravel': 'gravel', 'cloth': 'wool',
    'sand': 'sand', 'snow': 'snow', 'slime': 'slime_block', 'ladder': 'ladder', 'anvil': 'anvil', 'bamboo': 'bamboo',
    'bamboo_sapling': 'bamboo_sapling', 'scaffolding': 'scaffolding', 'lantern': 'lantern', 'sweet_berry_bush': 'sweet_berry_bush',
    'itemframe': 'wood',
}

# For the blocks no resource pack was asked about: the first of these that is found in the name of a block tells
# its kind of sound. They follow what the blocks are made of in Java
NAME_KINDS = [
    (r'wool|carpet', 'wool'),
    (r'cherry', 'cherry_wood'),
    (r'bamboo_(planks|mosaic|block|stairs|slab|double_slab|fence|door|trapdoor|button|pressure_plate|standing_sign|wall_sign|hanging_sign)', 'bamboo_wood'),
    (r'(crimson|warped)_(stem|hyphae)|stripped_(crimson|warped)', 'stem'),
    (r'nylium', 'nylium'),
    (r'(crimson|warped)_roots|nether_sprouts', 'roots'),
    (r'wart_block', 'wart_block'),
    (r'(crimson|warped)_fungus', 'fungus'),
    (r'crimson|warped', 'nether_wood'),
    (r'deepslate_brick', 'deepslate_bricks'),
    (r'deepslate_tile', 'deepslate_tiles'),
    (r'polished_deepslate', 'polished_deepslate'),
    (r'deepslate', 'deepslate'),
    (r'netherrack', 'netherrack'),
    (r'nether_brick', 'nether_bricks'),
    (r'basalt', 'basalt'),
    (r'soul_sand', 'soul_sand'),
    (r'soul_soil', 'soul_soil'),
    (r'ancient_debris', 'ancient_debris'),
    (r'netherite', 'netherite_block'),
    (r'bone_block', 'bone_block'),
    (r'amethyst', 'amethyst_block'),
    (r'copper', 'copper'),
    (r'tuff', 'tuff'),
    (r'calcite', 'calcite'),
    (r'dripstone', 'dripstone_block'),
    (r'mud_brick', 'mud_bricks'),
    (r'(^|:)mud$|muddy', 'mud'),
    (r'sculk', 'sculk'),
    (r'honey', 'honey_block'),
    (r'slime', 'slime_block'),
    (r'moss', 'moss'),
    (r'azalea', 'azalea'),
    (r'powder_snow', 'powder_snow'),
    (r'snow', 'snow'),
    (r'glass|(^|:|_)ice$|sea_lantern|beacon|glowstone', 'glass'),
    (r'concrete_powder|(^|:|_)sand$', 'sand'),
    (r'gravel|dirt|(^|:)clay$|farmland|podzol', 'gravel'),
    (r'grass|leaves|fern|flower|tulip|dandelion|poppy|orchid|allium|bluet|daisy|cornflower|lily|sapling|vine|hay|sponge|tnt|wheat|carrots|potatoes|beetroot|reeds|kelp|bush|mycelium|lilac|peony|rose', 'grass'),
    (r'planks|log|wood|oak|spruce|birch|jungle|acacia|mangrove|chest|barrel|crafting_table|bookshelf|lectern|loom|composter|noteblock|jukebox|(^|:)bed$|banner|sign|fence|campfire|beehive|bee_nest|'
     r'cartography|fletching|smithing|pumpkin|melon|mushroom_block', 'wood'),
    (r'ladder', 'ladder'),
    (r'iron|gold_block|diamond_block|emerald_block|lapis_block|redstone_block|hopper|cauldron|rail|bars|chain|anvil|lantern|(^|:)bell$|heavy_core', 'metal'),
]


def read_block_kinds(path):
    """The kinds of sound a resource pack names for its blocks. The file is not quite json, so it is not read as it."""
    with open(path) as file:
        text = file.read()
    kinds = {}
    for block in re.finditer(r'"([a-z0-9_:.]+)"\s*:\s*\{((?:[^{}]|\{[^{}]*\})*)\}', text):
        sound = re.search(r'"sound"\s*:\s*"([A-Za-z0-9_.]+)"', block.group(2))
        if sound and sound.group(1) in BEDROCK_KINDS:
            kinds[block.group(1)] = BEDROCK_KINDS[sound.group(1)]
    return kinds


def better(java_sound, other):
    """Of two Java sounds a Bedrock server tells alike, the one with the plainer name."""
    return other is None or (java_sound.count('.'), len(java_sound), java_sound) < (other.count('.'), len(other), other)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--sounds', required=True)
    parser.add_argument('--blocks')
    parser.add_argument('--output', default=os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources'))
    args = parser.parse_args()

    with open(args.sounds) as file:
        java_sounds = json.load(file)

    events = {}
    playsounds = {}
    for java_sound, bedrock in sorted(java_sounds.items()):
        java_sound = java_sound.removeprefix('minecraft:')
        playsound = bedrock.get('playsound_mapping')
        if playsound and better(java_sound, playsounds.get(playsound)):
            playsounds[playsound] = java_sound

        event = bedrock.get('bedrock_mapping')
        # What is told as an event of the level is another packet, and a block tells its own kind of sound
        if not event or bedrock.get('level_event') or (event in BLOCK_EVENTS and 'extra_data' not in bedrock):
            continue
        keys = []
        if 'identifier' in bedrock:
            keys.append('%s|%s' % (event, bedrock['identifier']))
        if 'extra_data' in bedrock:
            keys.append('%s|%d' % (event, bedrock['extra_data']))
        if java_sound.startswith('entity.') and java_sound.count('.') >= 2:
            keys.append('%s|%s' % (event, java_sound.split('.')[1]))
        elif not keys:
            keys.append(event)
        for key in keys:
            if better(java_sound, events.get(key)):
                events[key] = java_sound

    for table, corrections in ((events, EVENTS), (playsounds, PLAYSOUNDS)):
        for bedrock, java_sound in corrections.items():
            if java_sound is None:
                table.pop(bedrock, None)
            elif java_sound in java_sounds:
                table[bedrock] = java_sound
            else:
                raise SystemExit('%s is not a Java sound anymore, what is %s told as now?' % (java_sound, bedrock))

    with open(os.path.join(args.output, 'runtime_blocks.json')) as file:
        bedrock_blocks = sorted({block['bedrock_name'] for block in json.load(file).values()})
    named_kinds = read_block_kinds(args.blocks) if args.blocks else {}
    known_kinds = {name.split('.')[1] for name in java_sounds if name.startswith('block.') and name.endswith('.break')}
    blocks = {}
    for block in bedrock_blocks:
        kind = named_kinds.get(block.removeprefix('minecraft:'))
        if kind is None:
            kind = next((kind for pattern, kind in NAME_KINDS if re.search(pattern, block)), 'stone')
        if kind not in known_kinds:
            kind = 'stone'
        if kind != 'stone':
            blocks[block] = kind

    with open(os.path.join(args.output, 'sounds.json'), 'w') as file:
        json.dump({'events': dict(sorted(events.items())), 'playsounds': dict(sorted(playsounds.items())), 'blocks': blocks}, file, indent=1)
    print('%d events, %d sound names, %d of %d blocks that do not sound like stone (%d told by the resource pack)' % (
        len(events), len(playsounds), len(blocks), len(bedrock_blocks), sum(1 for block in bedrock_blocks if block.removeprefix('minecraft:') in named_kinds)))


if __name__ == '__main__':
    main()
