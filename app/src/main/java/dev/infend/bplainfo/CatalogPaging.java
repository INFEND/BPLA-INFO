package dev.infend.bplainfo;

/** Fixed-size, explicit continuation: never flood Android with the whole catalog. */
public final class CatalogPaging {
    private CatalogPaging(){}
    public static int end(int offset,int total,int chunk){
        if(offset<0||total<0||offset>total||chunk<1)
            throw new IllegalArgumentException("Invalid catalog page bounds");
        return offset+(int)Math.min((long)chunk,(long)total-offset);
    }
}
