#!/usr/bin/env node
// Aggregates the `android.json` profile of every built core into the registry
// bundled with the app.
import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const profiles = join(root, "build", "cores");
const target = join(root, "android", "app", "src", "main", "assets", "registry.json");

const ORDER = [
  "native32",
  "coolbar",
  "dingoo",
  "hyperscan",
  "lavax",
  "playdia",
  "spmp8000",
  "wqx",
  "bbk",
];

function hint(value) {
  if (value == null) return { en: "", zh: "" };
  if (typeof value === "string") return { en: value, zh: value };
  return { en: value.en ?? "", zh: value.zh ?? "" };
}

function normalize(profile) {
  return {
    id: profile.system,
    core: profile.core,
    so: profile.so_name,
    short: profile.short_name ?? profile.system,
    manufacturer: profile.manufacturer ?? "",
    names: {
      en: profile.display_name?.en ?? profile.system,
      zh: profile.display_name?.zh ?? profile.display_name?.en ?? profile.system,
    },
    extensions: profile.supported_extensions ?? [],
    firmware: (profile.firmware ?? [])
      .filter((entry) => entry && entry.file)
      .map((entry) => ({
        file: entry.file,
        required: entry.required === true,
        // Only emitted when set, so profiles that mark nothing keep the
        // registry compact and the flag stays opt-in.
        ...(entry.optional === true ? { optional: true } : {}),
        hint: hint(entry.hint),
      })),
    input: profile.input?.preset ?? "dpad-ab-select",
    orientation: profile.video?.orientation ?? "landscape",
    noGame: profile.no_game === true,
    bootOptions: (profile.boot_options ?? []).map((option) => ({
      key: option.key,
      label: hint(option.label),
      values: option.values ?? [],
    })),
    notes: hint(profile.notes),
  };
}

if (!existsSync(profiles)) {
  console.error(`No core profiles in ${profiles}; run scripts/build-cores.sh first.`);
  process.exit(1);
}

const systems = [];
for (const file of readdirSync(profiles)) {
  if (!file.endsWith(".json")) continue;
  const profile = JSON.parse(readFileSync(join(profiles, file), "utf8"));
  if (!profile.system || !profile.core || !profile.so_name) {
    console.error(`Skipping ${file}: missing system/core/so_name`);
    continue;
  }
  systems.push(normalize(profile));
}

systems.sort((a, b) => {
  const ia = ORDER.indexOf(a.id);
  const ib = ORDER.indexOf(b.id);
  return (ia < 0 ? ORDER.length : ia) - (ib < 0 ? ORDER.length : ib) || a.id.localeCompare(b.id);
});

mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, `${JSON.stringify({ schema: 1, systems }, null, 2)}\n`);
console.log(`registry.json: ${systems.map((system) => system.id).join(", ")}`);
