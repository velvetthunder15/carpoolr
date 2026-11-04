// carpoolr place search: Google Places API (New) Text Search, India only, biased to the rider.
// The key lives in the function secret GOOGLE_PLACES_KEY and never ships in the app.
const KEY = Deno.env.get("GOOGLE_PLACES_KEY") ?? "";

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });
  let q = "", lat: number | undefined, lng: number | undefined;
  try {
    const b = await req.json();
    q = String(b.q ?? "").trim().slice(0, 120);
    lat = typeof b.lat === "number" ? b.lat : undefined;
    lng = typeof b.lng === "number" ? b.lng : undefined;
  } catch { return json({ error: "bad body" }, 400); }
  if (q.length < 2) return json({ places: [] });
  if (!KEY) return json({ error: "no key" }, 503);

  const body: Record<string, unknown> = { textQuery: q, regionCode: "in", languageCode: "en", pageSize: 8 };
  if (lat !== undefined && lng !== undefined) {
    body.locationBias = { circle: { center: { latitude: lat, longitude: lng }, radius: 30000 } };
  }
  const r = await fetch("https://places.googleapis.com/v1/places:searchText", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": KEY,
      "X-Goog-FieldMask": "places.displayName,places.formattedAddress,places.location",
    },
    body: JSON.stringify(body),
  });
  if (!r.ok) return json({ error: `google ${r.status}`, detail: (await r.text()).slice(0, 300) }, 502);
  const data = await r.json();
  const places = (data.places ?? []).map((p: any) => ({
    name: p.displayName?.text ?? "",
    address: p.formattedAddress ?? "",
    lat: p.location?.latitude,
    lng: p.location?.longitude,
  })).filter((p: any) => p.name && typeof p.lat === "number");
  return json({ places });
});

function json(o: unknown, status = 200) {
  return new Response(JSON.stringify(o), { status, headers: { "Content-Type": "application/json" } });
}
