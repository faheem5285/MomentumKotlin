# Database launch crash recovery

## Cause

After the data classes moved into `com.faheem.habits.core.data`, Room generated a new schema identity hash, but the database version remained 9. Existing installs had the same table structure and records, but Room rejected the changed identity hash while the home screen loaded its database flows.

## Fix

- Advanced the Room database to version 10.
- Added migration 9 to 10 with no SQL changes. Room validates the unchanged tables and records the current identity hash.
- Kept the existing habit and loan entity definitions aligned with the installed database columns; no data is discarded.

## Device verification

- Installed and launched the debug build on the connected Android device.
- Confirmed the app process remained alive and the main activity resumed.
- Confirmed database version 10 and preserved existing rows: 3 habits and 2 completions.
- No crash was recorded after the update.
