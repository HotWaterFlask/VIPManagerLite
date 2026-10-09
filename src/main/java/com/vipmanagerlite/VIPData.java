package com.vipmanagerlite;

/**
 * VIP数据类
 * 用于表示玩家VIP信息
 */
public class VIPData {

    /**
     * VIP信息类
     */
    public static class VIPInfo {
        private final String vipGroup;      // VIP权限组
        private final String originalGroup; // 原权限组
        private final long expireTime;      // 过期时间（毫秒）
        private final boolean pendingRemoval; // 是否已标记待清理

        public VIPInfo(String vipGroup, String originalGroup, long expireTime) {
            this(vipGroup, originalGroup, expireTime, false);
        }

        public VIPInfo(String vipGroup, String originalGroup, long expireTime, boolean pendingRemoval) {
            this.vipGroup = vipGroup;
            this.originalGroup = originalGroup;
            this.expireTime = expireTime;
            this.pendingRemoval = pendingRemoval;
        }

        public String getVipGroup() {
            return vipGroup;
        }

        public String getOriginalGroup() {
            return originalGroup;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public boolean isPendingRemoval() {
            return pendingRemoval;
        }

        /**
         * 获取剩余时间（毫秒）
         */
        public long getRemainingTime() {
            long remaining = expireTime - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
    }
}
