USE bean_sprout;

-- 최근 저장·수정된 판매 데이터 확인
SELECT
    so.order_number,
    so.delivery_date,
    v.input_name AS vendor_name,
    si.item_name,
    si.quantity,
    si.unit_price,
    si.line_amount
FROM sales_items si
JOIN sales_orders so ON so.id = si.sales_order_id
JOIN vendors v ON v.id = so.vendor_id
ORDER BY so.delivery_date DESC, so.id DESC, si.id DESC
LIMIT 100;
