# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.1.10] - 2026-06-20
### Changed
- Updated ClojureScript to 1.12.145.
- Switched the dev entrypoint to a Node-backed ClojureScript REPL.
- Depends on `com.bhauman/rebel-readline` 0.1.10.

### Fixed
- Fixed alias completions by passing the current namespace as `:context-ns`.

## [0.1.1] - 2018-02-05
### Changed
- Documentation on how to make the widgets.

### Removed
- `make-widget-sync` - we're all async, all the time.

### Fixed
- Fixed widget maker to keep working when daylight savings switches over.

## 0.1.0 - 2018-02-05
### Added
- Files from the new template.
- Widget maker public API - `make-widget-sync`.

[Unreleased]: https://github.com/your-name/rebel-readline-cljs-service/compare/0.1.1...HEAD
[0.1.1]: https://github.com/your-name/rebel-readline-cljs-service/compare/0.1.0...0.1.1
