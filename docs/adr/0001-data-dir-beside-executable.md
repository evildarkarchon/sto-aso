---
status: accepted
---

# Data directory is resolved beside the executable, with CWD as fallback

The app reads its GameData CSVs, `admirals.xml`, `hashes.md5` and the icon cache from one data directory. Historically that was the current working directory, which breaks when the jar is launched from a shortcut or script whose "Start in" folder differs from where the jar and its data live. We now resolve the data directory as **the directory containing the running executable (jar or packaged EXE) if it contains `ships.csv`, otherwise the current working directory**. `ships.csv` is the marker because it ships with every release and must exist for the app to be useful, whereas `admirals.xml` does not exist on first run. The CWD fallback keeps IDE and development launches working, where the code source is a `classes/` directory with no data beside it.

## Consequences

- A user whose shortcut's working directory differs from the jar's folder will, after this change, read and write the `admirals.xml` beside the jar rather than the one in the working directory. Release notes should say so.
- Exactly one module (`AppBootstrap`) knows the rule; everything else receives a `Path`. Making the directory configurable later is a one-line change there.
