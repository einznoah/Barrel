<h1><b>Barrel</b><img src="https://github.com/BarrelMC/Assets/blob/master/logo/barrel.png" height="64" width="64" align="left" alt=""></h1><br>

<b>A proxy to connect to Minecraft: Bedrock Edition servers with Minecraft: Java Edition.</b><br>

Barrel runs between a Java Edition client and a Bedrock Edition server. The Java client joins Barrel as if it
were a Java server, Barrel joins the Bedrock server as if it were a Bedrock client, and translates every packet
between the two.

## Requirements

- Java 21, to build and to run
- Maven, to build
- Minecraft: Java Edition v26.3
- Bedrock Edition server v26.50 to v26.52 (protocol 2193) that is reached over RakNet, or over NetherNet at an
  address of its own (see [NetherNet](#nethernet)). Worlds a player hosts from inside the game, which are found
  over Xbox Live, are not supported.

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
with the default config on the same machine, and join. The Java client has to be signed in to its account, a
client that is not is sent away with `Failed to verify username!` or `Invalid session` unless the config says
`javaAuth: offline`.

## Configuration

| Option | Default | What it is |
| --- | --- | --- |
| `bindAddress` | `0.0.0.0` | The address the proxy listens on for Java clients. `127.0.0.1` only lets clients of the same machine in. |
| `port` | `25565` | The port the proxy listens on for Java clients. |
| `motd` | `Barrel Proxy\nConnect to Bedrock Server` | What the server list of the Java client shows. |
| `javaAuth` | `online` | `online` checks with Mojang that a Java player is signed in to the account it joins with, as a Java server with `online-mode` on does. With `offline` anybody can join under any name. |
| `javaWhitelist` | `false` | Whether only the Java accounts that are listed in `whitelist.json` are let in. See [Whitelist](#whitelist). |
| `bedrockAddress` | `play.venitymc.com` | The address of the Bedrock server. |
| `bedrockPort` | `19132` | The port of the Bedrock server. |
| `transport` | `raknet` | How the Bedrock server is reached: `raknet`, as most servers are, or `nethernet`. See [NetherNet](#nethernet). |
| `auth` | `online` | `online` signs players in to Xbox, `offline` joins with the name of the Java player for servers that do not ask for an Xbox account. |
| `rememberLogins` | `false` | Whether a player stays signed in to Xbox, also after the proxy was restarted. See below before turning it on. |

## Whitelist

With `javaWhitelist: true` only the Java accounts that are listed in `whitelist.json`, next to `config.yml`, are
let in. Everybody else is sent away with `You are not white-listed on this server!` before anything else is done
for them: they are not asked to sign in to Xbox, and the Bedrock server is not told of them.

The file is kept like the whitelist of a Java server, and the one of a Java server can be used as it is:

```json
[
  {"uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch"},
  {"uuid": "853c80ef-3c37-49fd-aa49-938b674adae6", "name": "jeb_"}
]
```

- An account is told by its `uuid`, written with or without the dashes. The `name` is only there for reading the
  file: it is not looked at, and an account that was given another name is still let in.
- The file is read again when it was changed, the proxy does not have to be restarted. Who is already playing
  stays until it leaves.
- When there is no file the proxy makes one that lists nobody.
- Who is turned away is printed with its uuid: `Steve (uuid ...) is not on the whitelist and was turned away`. To
  add a player, let it try to join once and copy the uuid from that line.
- A file that can not be read lets nobody in, and the proxy prints what is wrong with it. An entry without a uuid
  is left out and printed.

> [!WARNING]
> With `javaAuth: offline` the whitelist keeps nobody out: the uuid of a player is made of the name it says it
> has then, so whoever joins under a name that is on the list is let in.

## NetherNet

With `transport: nethernet` the proxy joins a server the way the game does it over NetherNet: it asks the server
over HTTPS or HTTP to be let in, and what is played then goes over WebRTC to an address the server names.

- `bedrockAddress` and `bedrockPort` are where the server answers to HTTP, the same address a Bedrock client is
  given for it. Whether that is HTTPS or HTTP the proxy finds out itself.
- The proxy has to reach that port over TCP, and over UDP the ports the server uses for WebRTC.
- The WebRTC part is not written in Java. It comes with the jar for Linux, Windows and macOS, each for x86-64 and
  ARM64.
- A server shows a key of its own when it answers. The game asks its player once whether to trust a key it has
  not seen before, the proxy takes the key the server shows.

## Signing in to Xbox

With `auth: online` a player that is not signed in yet lands in an empty world and is sent a link and a code in
the chat. After opening the link, entering the code and signing in with the Microsoft account, the chat says
`Successfully authenticated with Xbox Live. Please rejoin!`, and the next join goes to the Bedrock server.

Without `rememberLogins` a login is used once, every join asks for a new one. With `rememberLogins: true` a login
is kept, and saved as a file in the `accounts` folder next to the jar.

A login belongs to the Java account that signed in, not to its name: with `javaAuth: online` only a player that is
signed in to that Java account plays with it, also after the account was given another name. A login that was
saved under the name of a player by an older version is taken over the first time that player joins.

> [!WARNING]
> The files in `accounts` are the login to the Xbox accounts, do not share that folder. With `javaAuth: offline`
> the proxy does not check who a Java player is: whoever joins with the name of a player that is signed in plays
> with the Xbox account of that player. Then only turn `rememberLogins` on for a proxy nobody else can reach.

## What works

- **The world:** chunks, also from servers that send a chunk without its blocks and wait to be asked for them,
  blocks that change, one or many at once, and water a block is in, the Nether
  and the End
- **Moving:** walking, sprinting, sneaking, jumping and flying, also on servers that move the player themselves
  from the keys that are held and put the client back where they got to
- **Blocks:** breaking and placing, using what a block does when it is clicked on, doors, the blocks a Java
  client only draws when it is told what they hold, as chests, ender chests, shulker boxes and heads, two chests
  next to each other as one large chest, signs with
  what is written on them and writing on them, also a text that is longer than a line and that a Bedrock client
  goes on with in the next line by itself, beds and banners in their colors, the patterns of a banner, the
  plant in a flower pot, the book on a lectern, the mob in a spawner, what lies on a campfire or a shelf
- **Water:** putting a boat or a lily pad on the water the player looks at, and filling a bucket or a bottle from
  it
- **Entities:** mobs, other players with what they hold and wear, dropped items and picking them up, paintings,
  name tags,
  baby animals, the wool of sheep, creepers about to blow up, the swirls of effects; hitting and using entities
- **What is seen for a moment:** the lid of a chest, an ender chest or a shulker box going up and down, a bell
  swinging, players lying in their beds and getting up, with the screen to leave the bed, other players eating,
  drinking, raising a shield or drawing a bow, and what mobs show: the swing of a golem, hearts and smoke of
  taming and feeding, a wolf shaking itself dry, a sheep grazing, villagers angry or happy, a totem
- **Sounds:** what a server tells of a sound: chests, barrels and doors, bells and note blocks at their pitch, the
  records of a jukebox, mobs, blocks being placed, hit and broken by others with the pieces flying off, hits,
  explosions, and the sounds a command or an add-on plays by name. A player is not told again what its own client
  already makes heard
- **Riding:** seeing who rides what, riding along in a minecart, sitting on what a server or an add-on seats a
  player on, also an entity a Java client has no kind for (it is not seen then), and getting off with the sneak key
- **Steering** a boat or a saddled animal, on a server that moves what a player steers itself by the keys the
  player holds, as the dedicated server of Mojang does: the keys go to the server and the Java client is shown
  where the server has the boat or the animal. A boat goes and turns as it does for a Bedrock client, not as a
  Java client is used to, and the view does not turn with it. A horse goes where the player looks and jumps when
  the jump key is let go, without the bar a Java client shows for how strong the jump will be
- **The inventory:** the inventory of the player with armor and offhand, the creative inventory, chests,
  barrels and shulker boxes, furnaces, blast furnaces and smokers, brewing stands, dispensers, droppers and hoppers, and bundles: what is in
  them, putting items in and taking them out
- **Crafting:** the grid of the inventory and the crafting table with the recipe book, anvils, enchanting tables,
  stonecutters, smithing tables, grindstones, looms and cartography tables, trading with villagers
- **The player:** health and hunger, eating, bows, potions that are drunk, thrown or linger, effects, dying and
  respawning, game modes
- **Chat and commands:** what is said, the commands of the server with their names offered while typing, and what
  the server answers, also what a server sends as a text made of parts. A server sends many of its messages as
  the key of a text of the Bedrock client. The Java client has most of these texts itself and shows them in its
  own language, with the names of mobs, items and blocks in them, for the others the key is shown
- **The rest:** the time of day, the scoreboard on the side, the list of players

## Need implemented

- Reading the book on a lectern, setting up a beacon, and colors and styles inside the text of a sign
- Bundles in the creative inventory, and putting a bundle into a bundle
- What a command takes: the Java client is told the names of the commands, not what follows them
- Steering on a server that leaves moving a boat or an animal to the client, as PowerNukkitX does: a player can
  get in and out there, but what it sits in stays where it is
- Pistons moving: what they push is where it ends up at once
- Most particles, and of the sounds those a Bedrock client makes without being told: the steps of other players,
  the hit of the own player, music and what a place sounds like
- Light: every place is as bright as under the open sky, also caves, and the Nether is bright all over. A Bedrock
  server does not send light and the proxy does not work it out yet
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
- `The server put the player back 20 times within a minute, the last time to ...` — a server of Mojang moves the
  player itself from what is pressed, and puts the player back where it got to when the Java client got somewhere
  else. Now and then is as it should be, all the time means that the two do not move the player alike.
- `The server did not tell that the player has arrived in the other dimension, ...` — the proxy waited ten seconds
  for the server to end a change of dimension and went on without it.
- `Could not join the server over nethernet: ...` — the server did not answer as one of NetherNet at the address
  and port of the config, the line tells what happened instead. `Server offline ... Connection timed out` after
  that means the server let the player in, but the connection over UDP did not come about.
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
  blocks are still asked for (`SubChunkRequests`), the blocks as the server names them (`BedrockBlocks`) and who
  rides what (`Riding`).
- `auth` signs players in to Xbox and builds the login a Bedrock server asks for.

The blocks and items of both editions are in `src/main/resources/runtime_blocks.json` and `runtime_items.json`,
the Java sound for what a Bedrock server tells of a sound in `sounds.json`. They are made by the scripts in
[`tools`](tools), which say at their top where their input comes from, and have to be made again when the Java or
the Bedrock version changes.

Servers differ in how much they check. A server of Mojang asks for much that others do not: the keys a player
holds, when the loading screen and the inventory are open, which block is clicked on. What such a server sends
and expects can be looked up in [Mojang's documentation of the protocol](https://github.com/Mojang/bedrock-protocol-docs)
and in [ViaBedrock](https://github.com/RaphiMC/ViaBedrock), which does the same as Barrel for another client.

## Credits

- [TunnelMC](https://github.com/THEREALWWEFAN231/TunnelMC)
- [EZ4H](https://github.com/Project-EZ4H/EZ4H)
- [Cloudburst Protocol](https://github.com/CloudburstMC/Protocol) and [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib),
  the packets of the two editions
- [Cloudburst Network](https://github.com/CloudburstMC/Network) with
  [libdatachannel](https://github.com/paullouisageneau/libdatachannel), the connections over RakNet and NetherNet
- [MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth), the sign in to Xbox
- [Geyser](https://github.com/GeyserMC/Geyser) with its [mappings](https://github.com/GeyserMC/mappings) and the
  [mappings of ViaVersion](https://github.com/ViaVersion/Mappings), the blocks and items of the two editions
