# necessityQ — notes for Claude

- Before changing price ingestion (`firebase/functions/src`) or price math in the domain (`Prices.kt`, `Produce.kt`, `PriceCompare.kt`), read `docs/price-feeds-lessons.md`: known pitfalls of the Israeli price-transparency feeds (weighted units per 100 g, two XML dialects, Cerberus quirks, promotions, delta vs snapshot) and the open gaps in this repo.
