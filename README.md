# arcade-party-2
A Minigame for Minecraft: Java edition where players compete to win different smaller games.
Whoever wins the most games wins!

## Run using docker
With [Docker](https://docs.docker.com/) installed, you can use compose to start a production-ready server:

```bash
docker compose up -d
```

The image is saved between runs. If you update the project, make sure to rebuild the container to use the latest version of arcade-party-2:
```bash
docker compose build
```