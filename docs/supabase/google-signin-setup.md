# Google sign-in setup

1. In Google Cloud Console, create an OAuth client of type **Web application**.
   In **Authorized redirect URIs**, add the callback URL shown on the Supabase
   Google provider page. Save the Web client ID and secret securely.
2. Create an OAuth client of type **Android** for package `com.faheem.habits`.
   Add the SHA-1 fingerprint for each signing key you use. Obtain local
   fingerprints with `gradlew.bat signingReport`; add both debug and release
   fingerprints where applicable.
3. In Supabase Dashboard, open **Authentication → Providers → Google**, enable
   Google, add the Web and Android client IDs in the **Client IDs** field
   (comma-separated), and enter the Web client secret. Save the provider.
4. Put the Web client ID in your user-level `~/.gradle/gradle.properties`:

   ```properties
   GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   ```

   On Windows, `~/.gradle/gradle.properties` resolves under your Windows user
   profile. Do not commit it. Rebuild after adding it. The Google sign-in button
   is hidden when this property is empty.
5. In Supabase **Authentication → URL Configuration**, add
   `momentum://auth/reset` to the allowed redirect URLs for password recovery.
   Keep the project's existing HTTPS site URL as its production default.

Google ID tokens are requested through Android Credential Manager with a fresh
nonce. The app sends the SHA-256 hex digest to Google and the raw nonce to
Supabase Auth, as required by Supabase's nonce verification. If no Google account is available or
the user cancels the chooser, the email/password flow remains available.
