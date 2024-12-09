# UI demo

For now compling CSS requires `hyperfiddle` submodule access. To be fixed.

## Setup

```shell
npm install # ensure tailwind is installed
cd src/ui_demo
./compile_css.sh  # --watch – to recompile on file change
```

## Run

- uncomment `ui-demo.fiddles` in `src-dev/electric-fiddle.edn`
- `clj -A:dev` – no fiddle-specific alias