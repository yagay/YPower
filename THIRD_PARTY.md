# Third-party references and dependencies

YPower is an independent implementation. It uses the following libraries directly:

- **libsu 6.0.0** — Root shell / RootService foundation — Apache-2.0.
- **libxposed API 102.0.0** — modern Xposed module API — Apache-2.0.
- **libxposed Service 102.0.0** — module app ↔ Xposed framework communication — Apache-2.0.
- **ByteHook 1.1.2** — optional deep-mode Android PLT hook observer for native runtime diagnostics — MIT.
- AndroidX AppCompat / RecyclerView / Core — AndroidX licenses.

Design and diagnostic coverage were informed by publicly documented ideas from projects such as Shizuku, Sui, App Manager, LSPosed, RootBeer, RootRoot, Ruru, DuckDetector, DirtySepolicy, xCrash, Matrix and ShadowHook. ByteHook is now also a direct runtime dependency for optional deep native diagnostics.

No source code from GPL projects is copied into this repository. If a future change vendors or modifies third-party source/binaries, its exact license and notices must be added here before release.
