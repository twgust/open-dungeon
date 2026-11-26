package dev.opendungeon.core.model

/**
 * The orders OpenRouter ranks models in, server side (`sort` on `GET /models/user`).
 *
 * Offered are the orders that mean something for writing. Not offered: `decision-high-to-low` (decision
 * models only), `coding-` and `agentic-high-to-low` (retired indexes that no longer rank), and the
 * professional-domain indexes (legal, finance, healthcare and the like).
 */
public enum class ModelSort(public val wireName: String) {
    MostPopular("most-popular"),
    TopWeekly("top-weekly"),
    Newest("newest"),
    Intelligence("intelligence-high-to-low"),
    PriceLowToHigh("pricing-low-to-high"),
    PriceHighToLow("pricing-high-to-low"),
    ContextHighToLow("context-high-to-low"),
    Throughput("throughput-high-to-low"),
    Latency("latency-low-to-high"),
    Multilingual("multilingual-high-to-low"),
}
