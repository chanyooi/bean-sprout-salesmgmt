USE bean_sprout;

SHOW TABLES;

SELECT COUNT(*) AS 콩매입기록수 FROM bean_purchases;
SELECT COUNT(*) AS 콩사용기록수 FROM bean_usages;
SELECT COUNT(*) AS 재고부족설정수 FROM bean_stock_settings;
SELECT COUNT(*) AS 월비용기록수 FROM monthly_expenses;

SELECT *
FROM bean_purchases
ORDER BY purchase_date DESC, id DESC
LIMIT 20;

SELECT *
FROM bean_usages
ORDER BY usage_date DESC, id DESC
LIMIT 20;

SELECT *
FROM monthly_expenses
ORDER BY month_start DESC, expense_type ASC;
