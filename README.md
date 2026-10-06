<h1><b>Barrel</b><img src="https://github.com/BarrelMC/Assets/blob/master/logo/barrel.png" height="64" width="64" align="left" alt=""></h1><br>

<b>A proxy to connect to Minecraft: Bedrock Edition servers with Minecraft: Java Edition.</b><br>

Barrel runs between a Java Edition client and a Bedrock Edition server. The Java client joins Barrel as if it
were a Java server, Barrel joins the Bedrock server as if it were a Bedrock client, and translates every packet
between the two.

## Requirements

- Java 21, to build and to run
- Maven, to build
- Minecraft: Java Edition v26.3
- Bedrock Edition server v26.50/v26.51 (protocol 2193) that is reached over RakNet. Worlds that are only
  reachable over NetherNet, as those a player hosts from inside the game, are not supported.

## Getting started

Build the proxy:

```
mvn package
```

The jar is written to `target/Barrel-1.0.0-SNAPSHOT.jar`. It holds everything it needs.

Barrel reads `config.yml` from the folder it is started in and stops with `Config file not found!` when there is
none. Copy [`src/main/resources/config.yml`](src/main/resources/config.yml) next to the jar and set the address of
the Bedrock server in it, then start the proxy from that folder:

```
java -jar Barrel-1.0.0-SNAPSHOT.jar
```

The first line the proxy prints tells when the jar was built, which is the quickest way to tell whether a jar is
the one that was just built:

```
Starting Barrel Proxy software, built 2026-10-06 14:14 UTC
```

Add the proxy to the server list of the Java client with the address and port of the config, `localhost:25565`
with the default config on the same machine, and join.

## Configuration

| Option | Default | What it is |
| --- | --- | --- |
| `bindAddress` | `0.0.0.0` | The address the proxy listens on for Java clients. `127.0.0.1` only lets clients of the same machine in. |
| `port` | `25565` | The port the proxy listens on for Java clients. |
| `motd` | `Barrel Proxy\nConnect to Bedrock Server` | What the server list of the Java client shows. |
| `bedrockAddress` | `play.venitymc.com` | The address of the Bedrock server. |
| `bedrockPort` | `19132` | The port of the Bedrock server. |
| `auth` | `online` | `online` signs players in to Xbox, `offline` joins with the name of the Java player for servers that do not ask for an Xbox account. |
| `rememberLogins` | `false` | Whether a player stays signed in to Xbox, also after the proxy was restarted. See below before turning it on. |

## Signing in to Xbox

With `auth: online` a player that is not signed in yet lands in an empty world and is sent a link and a code in
the chat. After opening the link, entering the code and signing in with the Microsoft account, the chat says
`Successfully authenticated with Xbox Live. Please rejoin!`, and the next join goes to the Bedrock server.

Without `rememberLogins` a login is used once, every join asks for a new one. With `rememberLogins: true` a login
is kept, and saved as a file in the `accounts` folder next to the jar.

> [!WARNING]
> The proxy does not check who a Java player is. Whoever joins with the name of a player that is signed in plays
> with the Xbox account of that player, and the files in `accounts` are the login to that account. Only turn
> `rememberLogins` on for a proxy nobody else can reach, and do not share the `accounts` folder.

## What works

- **The world:** chunks, also from servers that send a chunk without its blocks and wait to be asked for them,
  blocks that change, one or many at once, and water a block is in
- **Moving:** walking, sprinting, sneaking, jumping and flying, also on servers that move the player themselves
  from the keys that are held and put the client back where they got to
- **Blocks:** breaking and placing, using what a block does when it is clicked on
- **Entities:** mobs, other players with what they hold and wear, dropped items and picking them up, name tags,
  baby animals, the wool of sheep, creepers about to blow up, the swirls of effects; hitting and using entities
- **The inventory:** the inventory of the player with armor and offhand, the creative inventory, chests,
  furnaces, blast furnaces and smokers, brewing stands, dispensers, droppers and hoppers
- **Crafting:** the grid of the inventory and the crafting table with the recipe book, anvils, enchanting tables,
  stonecutters, smithing tables, grindstones, looms and cartography tables, trading with villagers
- **The player:** health and hunger, eating, bows, potions that are drunk, thrown or linger, effects, dying and
  respawning, game modes
- **The rest:** chat, the time of day, the scoreboard on the side, the list of players

## Need implemented

- Beacons, signs and what else a block holds
- Commands, a message that starts with `/` is not sent as one
- The Nether and the End, a change of dimension is not told to the Java client
- Riding: boats, minecarts and animals
- Sounds and most particles
- Biomes, every place has the same one
- Forms, boss bars, and the resource packs of a server (the server is told the client has them)
- And More...

## When something does not work

The console of the proxy tells what a Bedrock server does not like:

- `The bedrock server did not accept <packet> (id ...): ...` — the server took a packet of the proxy for wrong and
  says why. After a login that is not taken, a second line tells what the login was made of, without what is
  secret in it.
- `The bedrock server did not take a change of the inventory: ...` — the server did not let the player move an
  item, with the reason of the server.
- A Java client that leaves with `Network Protocol Error` tells in its own log (`latest.log`) which packet it did
  not take, in the line that starts with `Failed to handle packet`.

These lines, together with the first line of the console, are what a bug report needs.

## Development

- `network/translator/bedrock` holds one class for each Bedrock packet that is translated to Java packets,
  `network/translator/java` one for each Java packet that is translated to Bedrock packets. Both are registered in
  `PacketTranslatorManager`. A packet that has no translator is dropped.
- `network/converter` turns blocks, items, entities, enchantments and potions of one edition into those of the
  other.
- `player` holds what the proxy has to remember of a player: the inventory with every container and crafting
  station (`Inventory`), what is sent to a server that moves the player itself (`PlayerInput`), the chunks whose
  blocks are still asked for (`SubChunkRequests`) and the blocks as the server names them (`BedrockBlocks`).
- `auth` signs players in to Xbox and builds the login a Bedrock server asks for.

The blocks and items of both editions are in `src/main/resources/runtime_blocks.json` and `runtime_items.json`.
They are made by the scripts in [`tools`](tools), which say at their top where their input comes from, and have to
be made again when the Java or the Bedrock version changes.

Servers differ in how much they check. A server of Mojang asks for much that others do not: the keys a player
holds, when the loading screen and the inventory are open, which block is clicked on. What such a server sends
and expects can be looked up in [Mojang's documentation of the protocol](https://github.com/Mojang/bedrock-protocol-docs)
and in [ViaBedrock](https://github.com/RaphiMC/ViaBedrock), which does the same as Barrel for another client.

## Credits

- [TunnelMC](https://github.com/THEREALWWEFAN231/TunnelMC)
- [EZ4H](https://github.com/Project-EZ4H/EZ4H)
- [Cloudburst Protocol](https://github.com/CloudburstMC/Protocol) and [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib),
  the packets of the two editions
- [MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth), the sign in to Xbox
- [Geyser](https://github.com/GeyserMC/Geyser) with its [mappings](https://github.com/GeyserMC/mappings) and the
  [mappings of ViaVersion](https://github.com/ViaVersion/Mappings), the blocks and items of the two editions
