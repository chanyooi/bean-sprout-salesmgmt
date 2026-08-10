USE bean_sprout;

SHOW TABLES;

SELECT COUNT(*) AS vendor_price_count
FROM vendor_prices;

SELECT
    v.input_name,
    vp.item_name,
    vp.unit_price,
    vp.source_sheet
FROM vendor_prices vp
JOIN vendors v ON v.id = vp.vendor_id
ORDER BY v.input_name, vp.item_name;

SELECT
    COUNT(*) AS sales_items_without_price
FROM sales_items
WHERE unit_price IS NULL;

SELECT
    so.order_number,
    so.delivery_date,
    v.input_name,
    si.item_name,
    si.quantity,
    si.unit_price,
    si.line_amount
FROM sales_items si
JOIN sales_orders so ON so.id = si.sales_order_id
JOIN vendors v ON v.id = so.vendor_id
ORDER BY so.delivery_date DESC, so.id DESC, si.id DESC;
