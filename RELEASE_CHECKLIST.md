# GitHub Release Checklist

## Before Tagging

- [ ] Update `README.md` if features/behavior changed
- [ ] Confirm app name localization (`OnlySight` / `唯见`)
- [ ] Verify no secrets in git diff
- [ ] Build debug APK successfully

## Versioning

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`
- [ ] Create git tag (example: `v1.0.0`)

## GitHub Release

- [ ] Create release notes (what changed, known limitations)
- [ ] Upload APK artifact
- [ ] Add installation instructions for friends/users

## Post Release

- [ ] Verify issue link in app settings (`GITHUB_ISSUES_URL`)
- [ ] Pin important issue templates/discussions if needed
