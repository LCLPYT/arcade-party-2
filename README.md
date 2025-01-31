# arcade-party-2
A Minigame for Minecraft: Java edition where players compete to win different smaller games.
Whoever wins the most games wins!

## Run using docker
With [Docker](https://docs.docker.com/) installed, you can use compose to start a production-ready server:

```bash
docker compose up --build -d
```

The container will be persisted between runs. 
That means, if you update the project, you may end up with outdated config files.
If the container fails to start after an update, you can remove the container: 
```bash
docker compose rm
```
After that, start the container as usual.