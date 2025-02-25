🚧 This mod is currently under construction.

# Langbot
Langbot uses AI image and text models to see what you are doing in Minecraft, and prompt you with various language learning scenarios.

For example, you might be building a road and the mod may prompt you:

`Welche Farbe hat die Straße?` -> `What colour is the road?`

## Roadmap / TODO
- [ ] Language selection
- [ ] Speedup (currently very slow!)
- [ ] Connect with local or hosted LLM'S
- [ ] Voice to text response.
- [ ] Give more context to LLM (e.g. Time of day, blocks in inventory, blocks looking at, item held)

## Installing

### Install Mod
1. Go to the `Actions` tab above.
2. Click on the newest action
3. Download the artifact zip
4. Unzip and install the contained `.jar` file

Note, you must also install `Fabric Loader` and `Fabric API`.

I recommend [MultiMc](https://multimc.org/) using to your mods and Minecraft instances.

### Install Local LLM
This relies on a local LLM management program called [Ollama](https://ollama.com/). To set this up, do the following:
1. Download Ollama and install
2. Run `ollama pull llava:7b` and `ollama pull llama3:8b`

This mod communicates with `Ollama` using it's built-in HTTP server on port `:11434`

## Usage
- Press `Y` to open a status check menu and run the process.
OR
- Type `/langbot`

This will take a screenshot, analyse the contents, and translate it into German.