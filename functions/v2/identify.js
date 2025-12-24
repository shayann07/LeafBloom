/* eslint-disable no-console */
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const Busboy = require("busboy");
const crypto = require("crypto");

// Secret (stored in Google Secret Manager)
const PLANTNET_API_KEY = defineSecret("PLANTNET_API_KEY");

// ---- Tuning knobs ----
const MAX_FILES = 5; // Pl@ntNet allows up to 5 images
const MAX_FILE_SIZE_BYTES = 6 * 1024 * 1024; // 6MB per image
const UPSTREAM_TIMEOUT_MS = 25_000; // Pl@ntNet request timeout
const DEFAULT_PROJECT = "all";
const DEFAULT_ORGAN = "leaf";
const DEFAULT_LANG = "en";
const DEFAULT_NB_RESULTS = 3;

// Small helpers
function json(res, status, payload) {
    res.set("Content-Type", "application/json; charset=utf-8");
    res.status(status).send(JSON.stringify(payload));
}

function toBool(val, defaultValue = false) {
    if (val === undefined || val === null) return defaultValue;
    const s = String(val).toLowerCase().trim();
    if (["1", "true", "yes", "y", "on"].includes(s)) return true;
    if (["0", "false", "no", "n", "off"].includes(s)) return false;
    return defaultValue;
}

function clampInt(val, fallback, min, max) {
    const n = Number.parseInt(String(val), 10);
    if (Number.isNaN(n)) return fallback;
    return Math.max(min, Math.min(max, n));
}

function safeProject(p) {
    const s = String(p || "").trim();
    if (!s) return DEFAULT_PROJECT;
    if (!/^[a-zA-Z0-9_-]+$/.test(s)) return DEFAULT_PROJECT;
    return s;
}

function safeOrgan(o) {
    const s = String(o || "").trim().toLowerCase();
    if (["leaf", "flower", "fruit", "bark", "auto"].includes(s)) return s;
    return DEFAULT_ORGAN;
}

function safeLang(l) {
    const s = String(l || "").trim().toLowerCase();
    if (!s) return DEFAULT_LANG;
    if (!/^[a-z]{2,3}([_-][a-z0-9]{2,8})?$/i.test(s)) return DEFAULT_LANG;
    return s.replace("_", "-");
}

async function fetchWithTimeout(url, options, timeoutMs) {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const resp = await fetch(url, { ...options, signal: controller.signal });
        return resp;
    } finally {
        clearTimeout(t);
    }
}

function normalizePlantNet(jsonBody) {
    const results = Array.isArray(jsonBody?.results) ? jsonBody.results : [];
    const mapped = results.map((r) => {
        const sp = r?.species || {};
        return {
            score: typeof r?.score === "number" ? r.score : null,
            scientificName:
                sp.scientificNameWithoutAuthor ||
                sp.scientificName ||
                null,
            scientificNameAuthorship: sp.scientificNameAuthorship || null,
            commonNames: Array.isArray(sp.commonNames) ? sp.commonNames : [],
            genus:
                sp?.genus?.scientificNameWithoutAuthor ||
                sp?.genus?.scientificName ||
                null,
            family:
                sp?.family?.scientificNameWithoutAuthor ||
                sp?.family?.scientificName ||
                null,
            gbifId: r?.gbif?.id ?? null,
            powoId: r?.powo?.id ?? null,
        };
    });

    return {
        bestMatch: jsonBody?.bestMatch || (mapped[0]?.scientificName ?? null),
        predictedOrgans: Array.isArray(jsonBody?.predictedOrgans) ? jsonBody.predictedOrgans : [],
        results: mapped,
        meta: {
            plantnetVersion: jsonBody?.version ?? null,
            remainingIdentificationRequests: jsonBody?.remainingIdentificationRequests ?? null,
        },
    };
}

exports.identifyPlantV2 = onRequest(
    {
        region: "asia-south1",
        cors: true,
        timeoutSeconds: 60,
        memory: "512MiB",
        concurrency: 10,
        maxInstances: 10,
        secrets: [PLANTNET_API_KEY],
    },
    async (req, res) => {
        const traceId = crypto.randomUUID();

        if (req.method === "OPTIONS") return res.status(204).send("");
        if (req.method !== "POST") {
            return json(res, 405, {
                ok: false,
                traceId,
                error: { code: "METHOD_NOT_ALLOWED", message: "Use POST." },
            });
        }

        const contentType = req.headers["content-type"] || "";
        if (!contentType.toLowerCase().startsWith("multipart/form-data")) {
            return json(res, 415, {
                ok: false,
                traceId,
                error: {
                    code: "UNSUPPORTED_MEDIA_TYPE",
                    message: "Expected multipart/form-data with image file(s).",
                },
            });
        }

        const fields = {};
        const files = [];
        let parseError = null;
        let fileTooLarge = false;

        try {
            const busboy = Busboy({
                headers: req.headers,
                limits: {
                    files: MAX_FILES,
                    fileSize: MAX_FILE_SIZE_BYTES,
                    fields: 30,
                },
            });

            busboy.on("field", (name, value) => {
                if (fields[name] === undefined) fields[name] = value;
                else if (Array.isArray(fields[name])) fields[name].push(value);
                else fields[name] = [fields[name], value];
            });

            busboy.on("file", (fieldname, file, info) => {
                const { filename, mimeType } = info || {};
                const safeName = filename || "upload.jpg";

                if (!["image/jpeg", "image/png"].includes(mimeType)) {
                    parseError = new Error(`Unsupported file type: ${mimeType}`);
                    file.resume();
                    return;
                }

                const chunks = [];
                file.on("data", (d) => chunks.push(d));
                file.on("limit", () => { fileTooLarge = true; });
                file.on("end", () => {
                    const buffer = Buffer.concat(chunks);
                    files.push({ fieldname, filename: safeName, mimeType, buffer });
                });
            });

            await new Promise((resolve, reject) => {
                busboy.on("error", reject);
                busboy.on("finish", resolve);
                busboy.end(req.rawBody);
            });
        } catch (e) {
            logger.error("Multipart parse failed", { traceId, err: String(e) });
            return json(res, 400, {
                ok: false,
                traceId,
                error: { code: "BAD_MULTIPART", message: "Failed to parse upload." },
            });
        }

        if (parseError) return json(res, 400, { ok: false, traceId, error: { code: "INVALID_FILE", message: parseError.message } });
        if (fileTooLarge) return json(res, 413, { ok: false, traceId, error: { code: "FILE_TOO_LARGE", message: `Image too large. Max ${Math.floor(MAX_FILE_SIZE_BYTES / (1024 * 1024))}MB per image.` } });
        if (!files.length) return json(res, 400, { ok: false, traceId, error: { code: "NO_FILE", message: "No image file found in form-data." } });

        const project = safeProject(fields.project ?? req.query.project ?? DEFAULT_PROJECT);
        const lang = safeLang(fields.lang ?? req.query.lang ?? DEFAULT_LANG);
        const organsField = fields.organs ?? fields.organ;
        const organListRaw = Array.isArray(organsField) ? organsField : organsField ? [organsField] : [];
        const organList = organListRaw.length === 1 && files.length > 1 ? Array(files.length).fill(organListRaw[0]) : organListRaw;
        const noReject = toBool(fields["no-reject"] ?? fields.noReject ?? req.query["no-reject"] ?? req.query.noReject, false);
        const includeRelatedImages = toBool(fields["include-related-images"] ?? fields.includeRelatedImages ?? req.query["include-related-images"], false);
        const detailed = toBool(fields.detailed ?? req.query.detailed, false);
        const nbResults = clampInt(fields["nb-results"] ?? fields.nbResults ?? req.query["nb-results"] ?? req.query.nbResults, DEFAULT_NB_RESULTS, 1, 10);

        if (organList.length > 0 && organList.length !== files.length) {
            return json(res, 400, { ok: false, traceId, error: { code: "ORGANS_MISMATCH", message: `organs count (${organList.length}) must match images count (${files.length}).` } });
        }

        const apiKey = PLANTNET_API_KEY.value();
        const qs = new URLSearchParams({ "api-key": apiKey, lang, "nb-results": String(nbResults) });
        if (noReject) qs.set("no-reject", "true");
        if (includeRelatedImages) qs.set("include-related-images", "true");
        if (detailed) qs.set("detailed", "true");
        const plantnetUrl = `https://my-api.plantnet.org/v2/identify/${encodeURIComponent(project)}?${qs.toString()}`;

        const form = new FormData();
        files.slice(0, MAX_FILES).forEach((f, idx) => {
            form.append("images", new Blob([f.buffer], { type: f.mimeType }), f.filename);
            const organ = organList.length ? safeOrgan(organList[idx]) : DEFAULT_ORGAN;
            form.append("organs", organ);
        });

        let upstreamResp;
        let upstreamText = "";
        let upstreamJson = null;

        const attempt = async () => {
            upstreamResp = await fetchWithTimeout(plantnetUrl, { method: "POST", body: form, headers: { Accept: "application/json" } }, UPSTREAM_TIMEOUT_MS);
            upstreamText = await upstreamResp.text();
            try { upstreamJson = upstreamText ? JSON.parse(upstreamText) : null; } catch { upstreamJson = null; }
        };

        try {
            await attempt();
            if (!upstreamResp.ok && upstreamResp.status >= 500) {
                await new Promise((r) => setTimeout(r, 400));
                await attempt();
            }
        } catch (e) {
            logger.error("Pl@ntNet request failed (network/timeout)", { traceId, err: String(e) });
            return json(res, 502, { ok: false, traceId, error: { code: "UPSTREAM_UNREACHABLE", message: "Plant identification service is unreachable. Try again." } });
        }

        if (!upstreamResp.ok) {
            if (upstreamResp.status === 404) {
                return json(res, 422, { ok: false, traceId, error: { code: "NO_PLANT_DETECTED", message: "No plant detected (or the image was rejected). Try a clearer close-up." }, upstream: { status: upstreamResp.status, body: upstreamJson || upstreamText || null } });
            }
            return json(res, 502, { ok: false, traceId, error: { code: "UPSTREAM_ERROR", message: "Plant identification failed." }, upstream: { status: upstreamResp.status, body: upstreamJson || upstreamText || null } });
        }

        const normalized = normalizePlantNet(upstreamJson);
        return json(res, 200, { ok: true, traceId, provider: "plantnet", data: normalized });
    }
);
