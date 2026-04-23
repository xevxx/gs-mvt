package org.geoserver.wms.mvt;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

public class VectorTileEncoderExtentTest {

    @Test
    public void testLegacy256TileSpaceStillScalesTo4096Extent() {
        VectorTileEncoder encoder = new VectorTileEncoder(4096, new Envelope(0, 256, 0, 256), 256d, 256d, false, 0d, 0d);

        List<Integer> commands =
                encoder.commands(new Coordinate[] {new Coordinate(0, 0), new Coordinate(256, 256)}, false);

        Assert.assertEquals(9, commands.get(0).intValue());
        Assert.assertEquals(0, commands.get(1).intValue());
        Assert.assertEquals(0, commands.get(2).intValue());
        Assert.assertEquals(10, commands.get(3).intValue());
        Assert.assertEquals(8192, commands.get(4).intValue());
        Assert.assertEquals(8192, commands.get(5).intValue());
    }

    @Test
    public void test4096TileSpaceDoesNotDoubleScale() {
        VectorTileEncoder encoder =
                new VectorTileEncoder(4096, new Envelope(0, 4096, 0, 4096), 4096d, 4096d, false, 0d, 0d);

        List<Integer> commands =
                encoder.commands(new Coordinate[] {new Coordinate(0, 0), new Coordinate(4096, 4096)}, false);

        Assert.assertEquals(9, commands.get(0).intValue());
        Assert.assertEquals(0, commands.get(1).intValue());
        Assert.assertEquals(0, commands.get(2).intValue());
        Assert.assertEquals(10, commands.get(3).intValue());
        Assert.assertEquals(8192, commands.get(4).intValue());
        Assert.assertEquals(8192, commands.get(5).intValue());
    }

    @Test
    public void testBufferedClipStillScalesFromUnbufferedTileWidth() {
        VectorTileEncoder encoder =
                new VectorTileEncoder(4096, new Envelope(-10, 266, -10, 266), 256d, 256d, false, 0d, 0d);

        List<Integer> commands =
                encoder.commands(new Coordinate[] {new Coordinate(-10, -10), new Coordinate(266, 266)}, false);

        Assert.assertEquals(zigZagEncode(-160), commands.get(1).intValue());
        Assert.assertEquals(zigZagEncode(-160), commands.get(2).intValue());
        Assert.assertEquals(zigZagEncode(4416), commands.get(4).intValue());
        Assert.assertEquals(zigZagEncode(4416), commands.get(5).intValue());
    }

    @Test
    public void testPlaceholderPixelSizeTracksConfiguredTileExtent() throws Exception {
        MVTWriter writer = MVTWriter.getInstance(
                new Envelope(0, 256, 0, 256), MVTWriter.DEFAULT_TARGET_CRS, 4096, 4096, 0, false, 0d, 0d);
        writer.setDisplaySize(256, 256);
        writer.setPixelSize(1);

        GeometryFactory gf = new GeometryFactory();
        Polygon polygon = gf.createPolygon(new Coordinate[] {
            new Coordinate(100, 100),
            new Coordinate(101, 100),
            new Coordinate(101, 101),
            new Coordinate(100, 101),
            new Coordinate(100, 100)
        });

        java.lang.reflect.Method method = MVTWriter.class.getDeclaredMethod("placeholderSameKind", Geometry.class);
        method.setAccessible(true);
        Geometry placeholder = (Geometry) method.invoke(writer, polygon);

        Assert.assertEquals(16d, placeholder.getEnvelopeInternal().getWidth(), 0.0001d);
        Assert.assertEquals(16d, placeholder.getEnvelopeInternal().getHeight(), 0.0001d);
    }

    private static int zigZagEncode(int n) {
        return (n << 1) ^ (n >> 31);
    }
}
