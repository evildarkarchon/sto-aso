Status: ready-for-agent
Blocked by: 01

# 04: Make Ship Filter visual verification a Gradle task

**What to build:** Let UI maintainers run every Ship Filter visual-baseline mode through one non-headless Gradle task instead of manually assembling Java and dependency classpaths.

- [ ] The task uses Java 25, the complete test runtime classpath, the project working directory, and standard Gradle argument forwarding.
- [ ] The task invokes the harness's existing flexible main method without changing its visibility or production Java behavior.
- [ ] Automated tests remain headless while the visual task explicitly runs non-headless and can display real Swing windows.
- [ ] Harness-owned scratch state is written beneath the Gradle build directory, while caller-selected image destinations remain controlled by task arguments.
- [ ] Current visual-baseline reproduction and interaction instructions use the dedicated Gradle task and no longer construct a classpath manually.
- [ ] At least one image-producing mode completes in a non-headless Windows session and its result is checked against the corresponding baseline under the documented display conditions.
- [ ] The interaction-smoke mode exits successfully through the same task.
- [ ] Running the active visual workflow does not recreate Maven-era output directories.
