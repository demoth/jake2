A high level (and subjective) overview of the code state at the moment of 2022.

| Module | Item                     | State                    | Comment                                                                    |
|--------|--------------------------|--------------------------|----------------------------------------------------------------------------|
| Common | Exception/Error handling | Todo                     | It just doesn't exist                                                      |
| Common | Logging                  | Todo                     | Migrate to an existing framework                                           |
| Common | File formats             | Todo                     | Review and generify to support other formats                               |
| Common | Network communication    | Todo                     | Review & restructure, migrate to modern approach                           |
| Common | Network protocol         | [Ok](info/Networking.md) |                                                         |
| Common | Collision code (BSP)     | Todo                     | Review & restructure, add support to qbsp2                                 |
| Client | 3D rendering             | Todo                     | Review & possibly migrate to an existing engine (LibGDX? JmonkeyEngine?)   |
| Client | GUI                      | Todo                     | Review & possibly migrate to an existing engine (LibGDX? JmonkeyEngine?)   |
| Client | Sound                    | Todo                     | Review & possibly migrate to an existing engine (LibGDX? JmonkeyEngine?)   |
| Client | Input                    | Todo                     | Review & possibly migrate to an existing engine (LibGDX? JmonkeyEngine?)   |
| Game   | Entity framework         | Not great                | Not terrible though, other alternatives (like ECS) will also have drawbacks |
| Game   | Character animation      | Todo                     | Decouple from AI and Monster logic                                         |
| Game   | AI                       | Todo                     | Decouple from animation                                                    |
| Game   | Weapon/Item logic        | Todo                     | Review & restructure                                                       |
| Server | Overall structure        | Todo                     | Review & restructure                                                       |
