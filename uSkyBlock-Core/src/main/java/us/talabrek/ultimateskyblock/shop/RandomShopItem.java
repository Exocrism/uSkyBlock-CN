package us.talabrek.ultimateskyblock.shop;

import java.util.Random;

public class RandomShopItem {
    private final ShopItem originalItem;
    private final double discountedPrice;
    private final int maxBuys;
    private final int giveAmount;
    private final int rarity;
    private final boolean hasUnmetCondition;
    private final int discountPercent;

    public RandomShopItem(ShopItem originalItem) {
        this(originalItem, 0);
    }

    public RandomShopItem(ShopItem originalItem, double penalty) {
        this.originalItem = originalItem;
        Random rnd = new Random();
        this.rarity = calcRarity(originalItem.getBaseCost());

        // 折扣率 20%~80%，5% 步进，按稀有度加权
        int[] tiers = {20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80};
        int roll = rollWeightedTier(rnd, rarity, tiers);
        this.hasUnmetCondition = penalty > 0;

        if (hasUnmetCondition) {
            // 条件未达成：翻转为加价 (+X%)
            this.discountPercent = -roll; // 负值表示加价
            this.discountedPrice = originalItem.getBaseCost() * (1.0 + roll / 100.0);
        } else {
            this.discountPercent = roll;
            double discount = (100.0 - roll) / 100.0;
            this.discountedPrice = originalItem.getBaseCost() * discount;
        }

        boolean limited = originalItem.getMaxBuys() > 0;

        if (limited) {
            // 限购物品：giveAmount × maxBuyTemp ≤ 原商品剩余限购
            int originalMax = originalItem.getMaxBuys();
            // maxBuyTemp：限购 1~3（高稀有度更少）
            int maxBuyTemp = switch (rarity) {
                case 1 -> 1;
                case 2 -> 1 + rnd.nextInt(2);
                case 3 -> 2 + rnd.nextInt(2);
                case 4 -> 3 + rnd.nextInt(2);
                default -> 4 + rnd.nextInt(3);
            };
            maxBuyTemp = Math.max(1, Math.min(maxBuyTemp, originalMax));
            // giveAmount 为 2 的倍数；原始为单数时给 1
            int maxPer = originalMax / maxBuyTemp;
            if (maxPer <= 0) {
                this.giveAmount = 1;
            } else if (maxPer == 1) {
                this.giveAmount = 1;
            } else {
                int half = maxPer / 2;
                this.giveAmount = Math.max(2, half * 2);
            }
            this.maxBuys = maxBuyTemp;
        } else {
            // 不限购物品
            this.giveAmount = switch (rarity) {
                case 1, 2 -> 1 + rnd.nextInt(4);             // 1~4
                case 3, 4 -> {
                    int base = 4 + rnd.nextInt(4);            // 4~7 → 乘4 → 16~28
                    yield base * 4;
                }
                default -> {
                    int base = 1 + rnd.nextInt(4);            // 1~4 → 乘16 → 16~64
                    yield base * 16;
                }
            };
            this.maxBuys = 0; // 不限购
        }
    }

    /**
     * 加权随机选择折扣档位。
     */
    static int rollWeightedTier(Random rnd, int rarity, int[] tiers) {
        int[] weights = new int[tiers.length];
        int total = 0;
        for (int i = 0; i < tiers.length; i++) {
            weights[i] = 1 + (5 - rarity) * (tiers.length - 1 - i) + (rarity - 1) * i;
            total += weights[i];
        }
        int roll = rnd.nextInt(total);
        int cum = 0;
        for (int i = 0; i < tiers.length; i++) {
            cum += weights[i];
            if (roll < cum) return tiers[i];
        }
        return tiers[0];
    }

    static int calcRarity(double baseCost) {
        if (baseCost >= 3000) return 1;
        if (baseCost >= 800)  return 2;
        if (baseCost >= 200)  return 3;
        if (baseCost >= 30)   return 4;
        return 5;
    }

    static int getRarityWeight(int rarity) {
        return switch (rarity) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            case 4 -> 6;
            default -> 8;
        };
    }

    public ShopItem getOriginalItem() { return originalItem; }
    public double getDiscountedPrice() { return discountedPrice; }
    public int getMaxBuys() { return maxBuys; }
    public int getGiveAmount() { return giveAmount; }
    public int getRarity() { return rarity; }
    public boolean hasUnmetCondition() { return hasUnmetCondition; }
    public int getDiscountPercent() { return discountPercent; }

    public String getDiscountColor() {
        if (hasUnmetCondition) return "\u00a7c";    // 加价 红色
        if (discountPercent >= 75) return "\u00a76";  // -75/-80 → 橙色
        if (discountPercent >= 55) return "\u00a7d";  // -55~-70 → 紫色
        if (discountPercent >= 30) return "\u00a79";  // -30~-50 → 蓝色
        return "\u00a7a";                                // -20/-25 → 绿色
    }

    /** 折扣百分比显示文本 */
    public String getDiscountLabel() {
        if (hasUnmetCondition) return "(+" + (-discountPercent) + "%)";
        return "(-" + discountPercent + "%)";
    }

    /** 是否不限购 */
    public boolean isUnlimited() { return maxBuys <= 0; }
}
