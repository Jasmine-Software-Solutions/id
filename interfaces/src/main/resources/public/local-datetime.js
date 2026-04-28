// local-datetime.js
(function () {
    function parseEpoch(value) {
        if (!value) return null;

        let epoch = Number(value);
        if (Number.isNaN(epoch)) return null;

        // If seconds (10 digits), convert to ms
        if (epoch < 1e12) {
            epoch *= 1000;
        }

        return new Date(epoch);
    }

    function formatDate(date) {
        return new Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "short",
            day: "numeric",
        }).format(date);
    }

    function formatTime(date) {
        return new Intl.DateTimeFormat(undefined, {
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    function formatDateTime(date) {
        return new Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "short",
            day: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    function processElement(el) {
        let attr =
            el.getAttribute("data-local-date") ??
            el.getAttribute("data-local-time") ??
            el.getAttribute("data-local-datetime");

        if (!attr) return;

        const date = parseEpoch(attr);
        if (!date) return;

        if (el.hasAttribute("data-local-date")) {
            el.textContent = formatDate(date);
        } else if (el.hasAttribute("data-local-time")) {
            el.textContent = formatTime(date);
        } else if (el.hasAttribute("data-local-datetime")) {
            el.textContent = formatDateTime(date);
        }
    }

    function process(root = document) {
        const selector =
            "[data-local-date], [data-local-time], [data-local-datetime]";
        root.querySelectorAll(selector).forEach(processElement);
    }

    // Initial run
    document.addEventListener("DOMContentLoaded", () => {
        process();
    });

    // HTMX support
    document.body.addEventListener("htmx:afterSwap", function (event) {
        process(event.target);
    });
})();