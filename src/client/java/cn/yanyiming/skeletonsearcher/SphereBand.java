package cn.yanyiming.skeletonsearcher;

public enum SphereBand {
    R250_200(200, 250, "250–200"),
    R200_150(150, 200, "200–150"),
    R150_100(100, 150, "150–100"),
    R100_50(50, 100, "100–50"),
    R50_25(25, 50, "50–25");

    private final int innerRadius;
    private final int outerRadius;
    private final String label;

    SphereBand(int innerRadius, int outerRadius, String label) {
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.label = label;
    }

    public int innerRadius() {
        return innerRadius;
    }

    public int outerRadius() {
        return outerRadius;
    }

    public String label() {
        return label;
    }

    public static SphereBand fromRadii(int inner, int outer) {
        for (SphereBand band : values()) {
            if (band.innerRadius == inner && band.outerRadius == outer) {
                return band;
            }
        }
        return null;
    }
}
