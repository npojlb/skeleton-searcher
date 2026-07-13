package cn.yanyiming.skeletonsearcher;

public enum OperationMode {
    INTERSECTION("交集"),
    UNION("并集"),
    DELAYED_INTERSECTION("三球后显示");

    private final String chineseName;

    OperationMode(String chineseName) {
        this.chineseName = chineseName;
    }

    public String chineseName() {
        return chineseName;
    }

    public boolean usesIntersectionMath() {
        return this != UNION;
    }

    public boolean shouldRender(int sphereCount) {
        return this != DELAYED_INTERSECTION || sphereCount >= 3;
    }
}
