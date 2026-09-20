# Impact methodology

ReLoop reports two kinds of numbers, and it keeps them strictly apart:

| Kind | Source | Confidence |
| --- | --- | --- |
| **Collected weight, collection counts, history** | `collected_waste` rows written when a collector records the *actual* weighed quantity | Measured |
| **Avoided CO₂e** | Collected weight × a fixed per-material coefficient | **Estimated** |

`GET /api/impact` therefore always returns `estimatesAreApproximations: true` and a `methodology`
string pointing at this document. The frontend repeats that caveat wherever CO₂e figures are shown.

> These figures are indicative only. They are **not** suitable for compliance reporting, carbon
> accounting, or offset claims.

## Where the numbers come from

Nothing is inferred from a scan, from an estimated pickup weight, or from an AI classification.
Only `collected_waste.quantity_kg` — the weight a verified collector recorded on site — feeds the
totals. If a pickup is cancelled, or is still in `REQUESTED` / `ACCEPTED` / `SCHEDULED`, it
contributes nothing.

## Coefficients

`kg CO₂e avoided per kg of material recycled`. Values are deliberately **conservative** and sit at
the low end of the ranges reported in widely cited lifecycle literature (EPA WARM-style magnitudes).
They are fixed constants in `ImpactService.CO2E_KG_PER_KG`.

| Material | Code | kg CO₂e / kg | Rationale |
| --- | --- | --- | --- |
| Plastic | `PLASTIC` | 1.5 | Virgin resin production and disposal avoided |
| Paper | `PAPER` | 1.0 | Pulp and landfill methane avoided |
| Cardboard | `CARDBOARD` | 0.9 | Lower-grade fibre, similar to paper |
| Metal | `METAL` | 4.0 | Primary aluminium is highly energy intensive |
| Glass | `GLASS` | 0.3 | Melting energy is modest and feedstock is abundant |
| E-waste | `E_WASTE` | 1.5 | Material recovery weighted conservatively |
| Organic | `ORGANIC` | 0.5 | Composting instead of landfilling |
| Textile | `TEXTILE` | 2.0 | Reuse displaces new fibre production |
| Hazardous | `HAZARDOUS` | 0.0 | No claim is made for hazardous streams |
| Other | `OTHER` | 0.5 | Mixed / unknown, low-confidence floor |

Total avoided CO₂e is the sum of `weight × coefficient` over each material, rounded to two
decimals. Per-category figures are returned in `ImpactResponse.byCategory`.

## Known limitations

- Coefficients are global averages, not grid- or region-specific.
- Transport emissions for collection are **not** subtracted.
- Actual recovery yield at the downstream facility is unknown to ReLoop; 100 % of the weighed
  material is credited.
- Hazardous waste is deliberately credited at `0.0`, because claiming an avoidance factor needs a
  certified disposal route that ReLoop cannot yet verify.

If you change a coefficient, update this table in the same commit — the API's `methodology` string
references this file by path.
