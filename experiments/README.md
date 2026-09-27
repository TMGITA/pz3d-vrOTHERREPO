# Standalone prototype

The first authorized milestone is the [OpenXR diagnostic](openxr-diagnostic/README.md). It uses a workspace-local SteamVR null-headset profile and independent Java/OpenGL rendering. It contains no game integration.

The next [renderer adapter](pz3d-adapter/README.md) transforms copied PZ3D classes to separate shared preparation from per-eye drawing. It is tested offline with synthetic fixtures and is not installed in the game.

The [ZombieBuddy harness](zombiebuddy-harness/README.md) packages that adapter as an opt-in stereo-capture and live desktop mirror test for the user to install and run.
