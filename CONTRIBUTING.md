# Short contribution guide for Jake

## How to help

You can help the project in a variety of ways:

- **testing**: submitting a bug if something doesn't work as expected; test on different platforms; test jake with
  different mods
- **art, modding & levelling**: create a free set of resources, that could be redistributed along with the engine (right
  now quake2 original files are required to run jake)
- **documentation**: create & review the documentation, especially dark sections, like BSP processing
- **development**: bring the project up to (jvm world) standards, simplify and optimize code
- **development**: pick an item from the issue board
- **development**: add more unit tests
- **development**: upgrade the platform & dependencies to new versions (most notably lwjgl3 and opengl3+)
- **have fun**: fun is important

## Git flow

Please stick to the git flow guidelines when writing commit messages and raising the PR.

## Code style

Since the majority of the code was adopted from the `C` codebase,
the current code-style is not consistent across the project.
However, new code should respect java code-style.
The default idea style is ok.

When renaming the critical functions, try to keep the old `C` name in the comment.
