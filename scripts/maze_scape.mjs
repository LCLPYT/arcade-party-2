import {argv, exit} from 'node:process';
import path from 'node:path';
import {promises as fs} from 'node:fs';

const dirArg = argv[2];

if (!dirArg) {
    console.error('Please specify a structure directory as first parameter');
    exit(1);
}

const dir = path.resolve(dirArg);

(async () => {
    const files = await fs.readdir(dir, {withFileTypes: true});
    const cfg = {};

    for (const file of files) {
        if (!file.isFile()) continue;

        const name = path.basename(file.name, '.nbt');

        cfg[name] = {
            weight: 1,
        }
    }

    console.log(JSON.stringify(cfg, null, 2));
})();