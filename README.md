# arcade-party-2
A minigame for Minecraft: Java edition where players compete to win different smaller games.
Whoever wins the most games wins!

Documentation available at: https://lclpyt.github.io/arcade-party-2-docs/ (WIP, very incomplete at the moment)

## Run using Docker / Podman
You can run this project using [Docker](https://docs.docker.com/) or [Podman](https://podman.io/).

You can run build and run the image directly in this repository, or you can use a prebuilt image and configure it yourself.

### Running directly
Before you can start the server, you'll need to agree to the [Minecraft EULA](https://www.minecraft.net/en-us/eula) (required):
```bash
echo "EULA=true" >> .env
```

You can then start the server:
```bash
docker compose up
```

If you want to rebuild the server image:
```bash
docker compose build
```

If you want to remove the image:
```bash
docker compose rm
```

### Using the prebuilt image
Prebuilt images are built from the CI and are available with:
```bash
docker pull ghcr.io/lclpyt/arcade-party-2:latest
```

The prebuilt image runs user `minecraft` (`1000:1000`) by default.

Ports to expose:
- `25565/tcp` for the Minecraft server
- `24454/udp` for the in-game voice-chat (optional)

Volumes:
- `/data` For the server data (read,write)

#### Bind mounting volumes
If you intend to use bind mounts for volumes, you need to pay attention to the file ownership.

Using Docker with "Rootful" / Non-Rootless (default), you need to make sure you run the container as your host user UID and GID.
If your host user also has UID 1000, you are good to go.
If not, this can be achieved with the `-u "$(id -u):$(id -g)"` option for `docker run ...` or with the `user: <uid>:<gid>` option in `docker-compose.yml`.
Otherwise, the container will create files owned by a different user on the host file system.

Using Docker Rootless, you need to run as root in the container using `-u "0:0"` or `user: 0:0` in `docker-compose.yml`.
This is because your host user is mapped to root inside the container.
Otherwise files created by the container will be owned by one of your subuids, which may or may not be a problem, depending in your setup.

Using Podman, you can make use of the `--userns=keep-id` option.
For that, specify `-u "$(id -u):$(id -g)" --userns=keep-id` for `podman run ...` or specify these in `docker-compose.yml`.
This will run as your UID/GID in the container while keeping the UID/GID mapping from the host system.
Alternatively you can also run as root user without the `userns=keep-id` option like with Docker Rootless; however this comes with the risk of running as root in the container.

## Developing
### Selective builds for development
Every minigame and mode is its own Gradle subproject, so building or running the full project compiles all of them. 
When working on a single minigame you can restrict the build to a subset to cut compile time. 
The shared `:lib` project is always included, and unless narrowed, all modes (currently just `default`) are included so the game is playable.

Selection is controlled by two keys, `ap2.games` and `ap2.modes`, each a comma separated list of subproject names. 
When neither is set, the full project is built (the default, also used by CI and release builds). 

From the Gradle CLI, pass them per invocation with `-P`:
```bash
# run the server with only Task Rush
./gradlew :runServer -Pap2.games=task_rush

# multiple minigames, and narrow modes explicitly
./gradlew :runServer -Pap2.games=task_rush,paintball -Pap2.modes=default
```

For IDEs, the "Minecraft Server" run configuration launches the server directly and does not run through Gradle, so its selection is fixed at Gradle sync time. 
Create the `dev.local.properties` file (ignored by Git) in the project root with your selection:
```properties
ap2.games=task_rush
```

Then re-sync Gradle. 
The generated "Minecraft Server" run configuration now compiles and loads only the selected minigames.
Delete the file (or remove the key) and re-sync to restore the full project. 
A `-P` property, when present, takes precedence over the file.

>[!NOTE] Using the `dev.local.properties` file will also disable any IDE features and removed the files from the index for excluded games / modes.

### Migrating to a new Minecraft version
When Minecraft updates and this project moves to a new default branch, update the following locations:

| File                            | Field                    | Example                  |
|---------------------------------|--------------------------|--------------------------|
| `gradle/libs.versions.toml`     | `minecraft = "..."`      | `"26.1.2"` -> `"26.2.0"` |
| `gradle.properties`             | `minecraft_compat = ...` | `26.1` -> `26.2`         |
| `.github/workflows/release.yml` | `branches:` trigger      | `"26.1"` -> `"26.2"`     |
| `docker/server.sh`              | `MC_VERSION="..."`       | `26.1.2` -> `"26.2"`     |
| `docker/server.sh`              | `FABRIC_VERSION="..."`   | `0.19.2` -> `"0.19.3"`   |

Also remember to upgrade the packwiz modpack for the docker image.
```
cd docker/packwiz
packwiz migrate minecraft <version>
```

### Create a new minigame
You can use the Python TUI helper script to bootstrap a new minigame.
Make sure to have the latest Python installed.

First, setup a virtual environment:
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r scripts/requirements.txt
```

Now, execute the TUI script:
```bash
python scripts/make_minigame.py
```
