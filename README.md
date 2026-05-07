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
This can be achieved with the `-u "$(id -u):$(id -g)"` option for `docker run ...` or with the `user: <uid>:<gid>` option in `docker-compose.yml`.
Otherwise, the container will create files owned by root on your host directory.

Using Docker Rootless, you need to run as root in the container using `-u "0:0"` or `user: 0:0` in `docker-compose.yml`.
This is because your host user is mapped to root inside the container.

Using Podman, you can make use of the `--userns=keep-id` option.
For that, specify `-u "$(id -u):$(id -g)" --userns=keep-id` for `podman run ...` or specify these in `docker-compose.yml`.
This will run as your UID/GID in the container while keeping the UID/GID mapping from the host system.
Alternatively you can also run as root user without the `userns=keep-id` option like with Docker Rootless; however this comes with the risk of running as root in the container.

## Developing
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