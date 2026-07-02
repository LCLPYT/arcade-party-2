{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" ];
      forEachSupportedSystem =
        f:
        nixpkgs.lib.genAttrs supportedSystems (
          system:
          f {
            pkgs = import nixpkgs { inherit system; };
          }
        );
    in
    {
      devShells = forEachSupportedSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk25

              # python is needed to execute helper scripts
              python3
            ];

            JAVA_HOME = "${pkgs.jdk25.home}";

            # Set up the Python virtual environment for helper scripts.
            shellHook = ''
              [ -d .venv ] || python -m venv .venv
              source .venv/bin/activate
              if [ scripts/requirements.txt -nt .venv/.installed ]; then
                pip install -r scripts/requirements.txt && touch .venv/.installed
              fi
            '';
          };
        }
      );
    };
}
