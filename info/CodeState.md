A high level (and subjective) overview of the code state at the moment of 2022.

| Module | Item                     | State                    | Comment                                                                     |
|--------|--------------------------|--------------------------|-----------------------------------------------------------------------------|
| Common | Exception/Error handling | Todo                     | It just doesn't exist                                                       |
| Common | Logging                  | Todo                     | Migrate to an existing framework                                            |
| Common | File formats             | Todo                     | Review and generify to support other formats                                |
| Common | Network communication    | Todo                     | Review & restructure, migrate to modern approach                            |
| Common | Network protocol         | [Ok](info/Networking.md) |                                                                             |
| Common | Collision code (BSP)     | Todo                     | Review & restructure, add support to qbsp2                                  |
| Client | 3D rendering/input/sound | WIP                      | Migrating to libGDX                                                         |
| Game   | Entity framework         | Not great                | Not terrible though, other alternatives (like ECS) will also have drawbacks |
| Game   | Character animation      | WIP                      | Decouple from AI and Monster logic                                          |
| Game   | AI                       | WIP                      | Decouple from animation                                                     |
| Game   | Weapon/Item logic        | Todo                     | Review & restructure, not priority atm.                                     |
| Server | Overall structure        | WIP                      | Review & restructure, move away from files with functions to proper classes |
