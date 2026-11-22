# Dedicated server

Install and launch from an interactive terminal at the repository root. Set
`basedir` to the directory containing `baseq2`.

```sh
./gradlew :dedicated:installDist
./dedicated/build/install/dedicated/bin/dedicated +set basedir /path/to/quake2 +map base1
```

Type a server command such as `status` and press Enter. End-of-input stops the
console reader. Press Ctrl+C to stop the server.
