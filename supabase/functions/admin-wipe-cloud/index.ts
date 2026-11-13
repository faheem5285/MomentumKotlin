import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const tables = [
  "momentum_loan_attachments",
  "momentum_loan_payments",
  "momentum_habit_reminders",
  "momentum_habit_skips",
  "momentum_completions",
  "momentum_life_rule_files",
  "momentum_vault_entries",
  "momentum_vault_keys",
  "momentum_todos",
  "momentum_loans",
  "momentum_loan_people",
  "momentum_contacts",
  "momentum_files",
  "momentum_expenses",
  "momentum_category_budgets",
  "momentum_life_rules",
  "momentum_custom_notifications",
  "momentum_habits",
];
const STALE_WIPE_LOCK_MS = 10 * 60 * 1000;

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

function configuredKey(jsonVariable: string): string | undefined {
  const raw = Deno.env.get(jsonVariable);
  if (!raw) return undefined;
  try {
    const keys = JSON.parse(raw) as Record<string, string>;
    return keys.default ?? Object.values(keys)[0];
  } catch {
    return undefined;
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json(405, { error: "POST required." });

  const authorization = request.headers.get("Authorization") ?? "";
  const accessToken = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!accessToken) return json(401, { error: "Sign in is required." });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? configuredKey("SUPABASE_PUBLISHABLE_KEYS");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? configuredKey("SUPABASE_SECRET_KEYS");
  if (!url || !anonKey || !serviceRoleKey) {
    return json(500, { error: "The admin wipe function is not configured." });
  }

  const authClient = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await authClient.auth.getUser(accessToken);
  if (authError || !user) return json(401, { error: "The sign-in session is invalid." });
  const roles = user.app_metadata?.roles;
  const isAdmin = user.app_metadata?.role === "admin" ||
    (Array.isArray(roles) && roles.includes("admin"));
  if (!isAdmin) {
    return json(403, { error: "This account is not allowed to wipe project data." });
  }

  const admin = createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  let lockTimestamp: string | null = null;
  try {
    const { data: wipeState, error: wipeStateError } = await admin
      .from("momentum_wipe_state")
      .select("generation,wipe_in_progress,updated_at")
      .eq("id", 1)
      .maybeSingle();
    if (wipeStateError || !wipeState) {
      throw new Error("Apply the updated docs/supabase-schema.sql first; momentum_wipe_state is missing.");
    }

    const lockAge = Date.now() - Date.parse(wipeState.updated_at);
    if (wipeState.wipe_in_progress && (!Number.isFinite(lockAge) || lockAge < STALE_WIPE_LOCK_MS)) {
      return json(409, { error: "A project wipe is already running. Wait, then retry if it did not finish." });
    }

    // Claim the lock with a compare-and-set update. Updated app and Storage
    // policies reject user writes until the completed generation is published.
    const lockTime = new Date().toISOString();
    const { data: claimed, error: claimError } = await admin
      .from("momentum_wipe_state")
      .update({ wipe_in_progress: true, updated_at: lockTime })
      .eq("id", 1)
      .eq("wipe_in_progress", Boolean(wipeState.wipe_in_progress))
      .eq("updated_at", wipeState.updated_at)
      .select("id")
      .maybeSingle();
    if (claimError || !claimed) {
      throw new Error(`Could not start the project wipe${claimError ? `: ${claimError.message}` : ". Another request may have started it."}`);
    }
    lockTimestamp = lockTime;

    let deletedRows = 0;
    for (const table of tables) {
      const idColumn = table === "momentum_vault_keys" ? "user_id" : "id";
      const { count, error } = await admin.from(table).delete({ count: "exact" })
        .not(idColumn, "is", null);
      if (error) throw new Error(`Could not clear ${table}: ${error.message}`);
      deletedRows += count ?? 0;
    }

    const bucket = admin.storage.from("momentum-files");
    let deletedObjects = 0;
    const folders = [""];
    const objectPaths: string[] = [];
    while (folders.length > 0) {
      const folder = folders.pop()!;
      let offset = 0;
      while (true) {
        const { data, error } = await bucket.list(folder, { limit: 1000, offset });
        if (error) throw new Error(`Could not list stored files: ${error.message}`);
        for (const entry of data ?? []) {
          const path = folder ? `${folder}/${entry.name}` : entry.name;
          if (entry.id == null) folders.push(path);
          else objectPaths.push(path);
        }
        if (!data || data.length < 1000) break;
        offset += data.length;
      }
    }
    for (let index = 0; index < objectPaths.length; index += 1000) {
      const batch = objectPaths.slice(index, index + 1000);
      const { error } = await bucket.remove(batch);
      if (error) throw new Error(`Could not remove stored files: ${error.message}`);
      deletedObjects += batch.length;
    }

    // Publish the reset only after every table and storage object was cleared.
    // Auth users and their server-side sessions remain untouched.
    const completedAt = new Date().toISOString();
    const { data: completed, error: completeError } = await admin
      .from("momentum_wipe_state")
      .update({
        generation: Number(wipeState.generation) + 1,
        wipe_in_progress: false,
        updated_at: completedAt,
      })
      .eq("id", 1)
      .eq("wipe_in_progress", true)
      .eq("updated_at", lockTimestamp)
      .select("generation")
      .maybeSingle();
    if (completeError || !completed) {
      throw new Error(`Cloud data was deleted, but the reset marker could not be completed${completeError ? `: ${completeError.message}` : ". Retry the wipe."}`);
    }
    lockTimestamp = null;

    return json(200, {
      deletedRows,
      deletedObjects,
      authUsersPreserved: true,
      wipeGeneration: completed.generation,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Cloud wipe failed.";
    if (lockTimestamp) {
      // Keep writes blocked after a partial delete. Mark the completed failed
      // attempt as stale so an admin can safely claim and retry immediately.
      const retryableAt = new Date(Date.now() - STALE_WIPE_LOCK_MS - 1_000).toISOString();
      const { error: recoveryError } = await admin
        .from("momentum_wipe_state")
        .update({ updated_at: retryableAt })
        .eq("id", 1)
        .eq("wipe_in_progress", true)
        .eq("updated_at", lockTimestamp);
      return json(500, {
        error: message,
        localDataPreserved: true,
        cloudCleanupMayBePartial: true,
        retryAllowed: !recoveryError,
        staleLockRecoveryRequired: Boolean(recoveryError),
      });
    }
    return json(500, { error: message });
  }
});
