# Application Update

Application Update owns the stable Anilib application release channel. It is
separate from `Updates`, which checks library titles, and from Extension
Repository, which updates user-installed source Bundles.

The Bundle publishes a shared service and presentation. The service compares
the packaged semantic version with the latest stable GitHub release through the
Anilib HTTP capability. The shared About screen renders the result on Android
and desktop and can open the HTTPS release page through its platform browser
adapter.

The feature does not silently download or install application binaries. Those
operations remain platform-owned release work and require signed production
artifacts.
