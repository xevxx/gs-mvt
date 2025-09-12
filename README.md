# GeoServer MVT Extension (enhanced)
## Overview

This extension adds Mapbox Vector Tiles (MVT) output to GeoServer WMS and provides a Slippy Map endpoint that forwards to WMS.
Tiles render nicely in WebGL clients such as MapLibre GL JS and Mapbox GL JS.

## Highlights vs. upstream:

Small geometry handling: small_geom_mode=drop|pixel|keep with pixel_size and PIXEL_AS_POINT options.

Attribute stripping: strip_attributes=true (with optional keep_attrs=name1,name2 whitelist).

Custom MIME (optional) so you don’t collide with GeoServer’s stock VectorTiles output.

Slippy Tiles controller restored and Spring-wired; unit test included.

## Compatibility

GeoServer: 2.24+ (developed/tested with 2.26.x)

Java: 17 (Eclipse Adoptium / Temurin recommended)

Maven: 3.8+ (tested with 3.9.x)

If you build older branches, match the GeoServer/GeoTools API split (org.geotools.api.*).

## Build
``` # Java 17 on PATH
mvn -V -DskipTests clean package
```
If Spotless fails with a formatter warning, ensure google-java-format is the expected version in the POM.
On Windows, if .spotless-index appears, ignore it (add to .gitignore).

The build produces a JAR in target/. Drop it into $GEOSERVER_WEBAPP/WEB-INF/lib and restart.

# Output Formats / MIME Types

By default we advertise the standard MVT type:

application/vnd.mapbox-vector-tile

If you’re running GeoServer’s stock VectorTiles plugin and want to avoid conflicts, you can switch to a custom type in org.geoserver.wms.mvt.MVT:
```
public interface MVT {
    String MIME_TYPE = "application/x-mvt-custom";
    Set<String> OUTPUT_FORMATS = java.util.Collections.unmodifiableSet(
        new java.util.HashSet<>(java.util.Arrays.asList(
            MIME_TYPE,
            "application/x-mvt-pbf"
        )));
}
```
Then request with:
```
&format=application/x-mvt-custom
```
The plugin registers formats from MVT.OUTPUT_FORMATS, and MVTStreamingMapOutputFormat#getMimeType() returns MVT.MIME_TYPE.

## WMS Usage

Minimal WMS example (change MIME if you chose the custom one):
```
/geoserver/wms?
  SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&
  LAYERS=workspace:layer&
  STYLES=&FORMAT=application/vnd.mapbox-vector-tile&
  SRS=EPSG:3857&WIDTH=256&HEIGHT=256&
  BBOX=minx,miny,maxx,maxy

```

## ENV parameters (case-insensitive)
| Key                  | Type    | Default | Description                                                                                                            |   |
|----------------------|---------|---------|------------------------------------------------------------------------------------------------------------------------|---|
| gen_level            | enum    | MID     | Generalisation table to use: LOW, MID, HIGH.                                                                           |   |
| gen_factor           | double  | n/a     | Overrides table. ≤ 0 disables generalisation (no simplification).                                                      |   |
| small_geom_threshold | double  | 0.05    | In tile units (post-transform). Lines shorter or polygons with smaller area are considered “small”.                    |   |
| small_geom_mode      | enum    | drop    | What to do with “small” geometries: drop (skip), keep (leave as is), pixel (replace with a 1-pixel placeholder).       |   |
| pixel_size           | int     | 1       | Size (in display pixels) of the placeholder when small_geom_mode=pixel.                                                |   |
| PIXEL_AS_POINT       | boolean | false   | If true and small_geom_mode=pixel, collapse any small geom to a single Point at centroid (otherwise: type-preserving). |   |
| strip_attributes     | boolean | false   | If true, strip attributes from all features (not just placeholders).                                                   |   |
| keep_attrs           | csv     | n/a     | Comma-separated whitelist of attribute names to keep even when strip_attributes=true.                                  |   |
| avoid_empty_proto    | boolean | false   | Emit an empty Layer message so tiles are not 0-byte (helps some caches).                                               |   |

## Notes

When small_geom_mode=pixel, the encoder’s internal skipping threshold is automatically neutralized to avoid dropping placeholders. Detection still uses your small_geom_threshold.

Placeholders:

Polygons → either a 1×1 tile-unit square or a Point (if PIXEL_AS_POINT=true).

Lines → short dash centered at centroid or Point (with PIXEL_AS_POINT=true).

Points → unchanged.

## Slippy Maps - advice unchanged but had to wire up differently to stop it interfering with geoserver rest

Spring wiring

src/main/resources/applicationContext-slippymap.xml (already included) enables the controller:
```
<mvc:annotation-driven/>
<context:component-scan base-package="org.geoserver.slippymap"/>

<bean id="slippyTilesController" class="org.geoserver.slippymap.SlippyTilesController">
  <property name="defaultBuffer" value="10"/>
  <property name="defaultFormat" value="application/vnd.mapbox-vector-tile"/>
  <property name="defaultStyles" value=""/>
  <property name="supportedOutputFormats">
    <map>
      <entry key="pbf" value="application/vnd.mapbox-vector-tile"/>
    </map>
  </property>
  <property name="defaultTileSize">
    <map>
      <entry key="pbf" value="256"/>
    </map>
  </property>
</bean>

```
If you switched to the custom MIME, set both defaultFormat and the pbf entry to application/x-mvt-custom.
Ensure org.geoserver.wms.mvt.MVT is public if you reference constants from Spring or other packages.

## Troubleshooting

Tiles same size at all zooms
You likely send attributes; use ENV=strip_attributes:true (and optional keep_attrs) to drop them.

Grid seams at tile joins
Use small_geom_mode=pixel (placeholders), or keep attributes minimal.

0-byte tiles break cache
Use ENV=avoid_empty_proto:true.

Placeholders not appearing
Ensure small_geom_mode=pixel and your small_geom_threshold is large enough to classify features as “small”.
In pixel mode the encoder’s drop threshold is disabled internally so placeholders won’t be pruned.

## Tests

SlippyTilesControllerTest asserts that a slippy request forwards to the correct WMS URL and that responses match byte-for-byte.
If you change defaults (MIME/tileSize), update the Spring bean and the test expectations accordingly.

## License

Same as upstream project. See LICENSE in the repo.

# Previous Info Below

# Geoserver MVT Extension

## Overview

This extension for the **Geoserver** adds the possibility to deliver [Mapnik Vector Tiles](https://github.com/mapbox/mapnik-vector-tile/) in
Protocol Buffers outputformat as result of an **WMS** or **Slippy Map Tile** request.

The resulting Vector Tiles can e.g. be rendered by WebGL JS clients like [Mapbox GL JS](https://www.mapbox.com/mapbox-gl-js/api/)
or [MapLibre GL JS](https://maplibre.org/projects/maplibre-gl-js/).

### Geoserver Version Support

#### Version 0.5.X
Version 0.5.X has been developed and tested with [Geoserver 2.26](http://geoserver.org) but should also work with versions
starting from 2.24. 

0.5.X just migrates to the changes in org.openapi packages to org.geotools.api described in the [Developer updates section](https://geoserver.org/announcements/2023/09/25/geoserver-2-24-RC-released.html)
since Geoserver Version 2.24 RC.

##### Changes from 0.4.X to 0.5.X
none

#### Version 0.4.X 
Version 0.4.X has been developed and tested with [Geoserver 2.23](http://geoserver.org) but might also work with preceding
versions.

After several changes in Geoserver dependencies around Version 2.14 (JTS version with changed package definitions, Geotools Changes, ... )
version 0.4.X of the PlugIn will not work in earlier versions.

See [Branch 0_4_x_pre_geoserver_2.24.RC](https://github.com/emplexed/gs-mvt/tree/0_4_x_pre_geoserver_2.24.RC) and use
[Release 0.4.3](https://github.com/emplexed/gs-mvt/releases/tag/v0.4.3) for Geoserver Versions between 2.14 and 2.23.

##### Changes from 0.3.X to 0.4.X

* returned Mime-Type of output is application/vnd.mapbox-vector-tile
* default format name has changed from ```application/x-protobuf``` to ```application/vnd.mapbox-vector-tile```
* introduced additional ```env``` (avoid 0 byte protos to play nicer
  with [GeoWebCache](https://docs.geoserver.org/main/en/user/geowebcache/index.html))

#### Version 0.3.X
For usage of the PlugIn in pre 2.14 Geoserver Version
see [Branch 0_3_x_pre_geoserver_2.14.X](https://github.com/emplexed/gs-mvt/tree/0_3_x_pre_geoserver_2.14.X) and use
[Release 0.3.3](https://github.com/emplexed/gs-mvt/releases/tag/v0.3.3).


## Getting Started

For building the plugin the geoserver source code is required. It is available at the
[boundless maven repository](http://repo.boundlessgeo.com/main/) or on [GitHub](https://github.com/geoserver).
You can build ```gs-mvt-0.4.X.jar``` with maven or directly download it from [here](https://github.com/emplexed/gs-mvt/releases).
In order to get started the ```gs-mvt-0.4.X.jar``` package and the depending library ```protobuf-java-3.24.2.jar``` have to be copied to the
geoserver's lib directory ```geoserver/WEB-INF/lib```. After starting the GeoServer the format
```application/vnd.mapbox-vector-tile``` is shown in the WMS Format list of the layer preview.

## WMS Query

Mandatory for the WMS **getMap** request are the ```bbox```, ```witdh``` and ```height``` parameters. The ```bbox``` describes the requested
source coordinate system. The ```width``` and ```height``` the target coordinate system (tile local). The MVT format uses a local coordinate
system within each tile. By default this is 0 to 256 on x and y axis. An example WMS Request could be:

```
http://localhost/geoserver/test/wms?STYLES=&LAYERS=test:streetsegments&FORMAT=application/vnd.mapbox-vector-tile&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&SRS=EPSG:4326&bbox=9.499057554653266,46.410057509506515,17.1504898071289,49.0165481567383&WIDTH=256&HEIGHT=256
```

As result all features of the requested clip are returned. Additionally
a [TopologyPreservingSimplifier](http://www.vividsolutions.com/jts/javadoc/com/vividsolutions/jts/simplify/TopologyPreservingSimplifier.html)
simplifies these geometries. By default the algorithm uses a generalisation factor based on the zoom level. See Generalisation.

## Generalisation

### Generalisation Levels

The default behaviour of the plugin choose a generalisation factor based on the zoom level. Large zoom levels will be generalised with a
smaller factor then smaller ones.

The Plug-In provides 3 sets of different generalisation configurations (LOW,MID,HIGH) which can be selected by adding the **gen_level** ENV
parameter to the WMS request (or apply the parameter to the Slippy Map Tiles Request).

An example WMS Request could be:

```
http://localhost/geoserver/test/wms?STYLES=&LAYERS=test:streetsegments&FORMAT=application/vnd.mapbox-vector-tile&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&SRS=EPSG:4326&bbox=9.499057554653266,46.410057509506515,17.1504898071289,49.0165481567383&WIDTH=256&HEIGHT=256&ENV=gen_factor:LOW
```

**Generalisation Configuration**

 Zoom | gen_factor - LOW | gen_factor - MID | gen_factor - HIGH 
------|------------------|------------------|-------------------  
 0    | 0.7              | 0.8              | 1.0               
 1    | 0.7              | 0.8              | 1.0               
 2    | 0.6              | 0.8              | 1.0               
 3    | 0.6              | 0.7              | 1.0               
 4    | 0.6              | 0.7              | 1.0               
 5    | 0.6              | 0.7              | 1.0               
 6    | 0.6              | 0.7              | 1.0               
 7    | 0.6              | 0.7              | 0.9               
 8    | 0.6              | 0.7              | 0.9               
 9    | 0.5              | 0.6              | 0.8               
 10   | 0.4              | 0.5              | 0.7               
 11   | 0.35             | 0.45             | 0.7               
 12   | 0.3              | 0.4              | 0.7               
 13   | 0.25             | 0.35             | 0.6               
 14   | 0.2              | 0.3              | 0.5               
 15   | 0.15             | 0.25             | 0.4               
 16   | 0.1              | 0.2              | 0.3               
 17   | 0.05             | 0.1              | 0.2               
 18   | 0.01             | 0.05             | 0.1               
 19   | 0.005            | 0.025            | 0.05              
 20   | 0.001            | 0.01             | 0.01              

**per default MID is used**

### Supplying the generalisation factor directly

If the configuration shiped in the Plug-In dosn´t suite the needs of a data set or any special behaviour is required the generalisation
level can be overwritten by using the WMS ENV parameter gen_factor (or apply the parameter to the Slippy Map Tiles Request). If this factor
is present in the WMS Request it will be applied to the generalisation algorithm, the value defined in the generalisation level will not be
used in this request.

**zero or negative gen_factor can be used to turn the generalisation off!**

A typically use case could be:

* fixed generalisation level
* custom generalisation set per zoom level (e.g. in conjunction with a client that alters the factor in the url when the map is zoomed)
* turning generalisation off

An example WMS request could be:

```
http://localhost/geoserver/test/wms?STYLES=&LAYERS=test:streetsegments&FORMAT=application/vnd.mapbox-vector-tile&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&SRS=EPSG:4326&bbox=9.499057554653266,46.410057509506515,17.1504898071289,49.0165481567383&WIDTH=256&HEIGHT=256&ENV=gen_level:0.07
```

## Configure Small Geometries Output

Per default the Plug-In will skip small geometries (short lines or polygons with small areas). The treshold is currently fixed with 0.05.
This behaviour can be turned changed by setting the WMS ENV Parameter small_geom_threshold to a double value (or apply the parameter to the
Slippy Map Tiles Request).

A positive double value means that lines shorter or polygons with a smaller area are skipped in output. If the parameter is zero or negative
no geometries will be skipped in output.

## Avoid output of 0 Byte Protobuf
If a tile does not contain any feature (in any layer) per default the returned payload will be empty (0 byte). 
This can cause issues in clients e.g. using a GeoWebCache has issues with empty (0 byte) tiles. 

Using the ENV Parameter ```avoid_empty_proto``` with value ```true``` (or Slippy Map Formats ```avoid_empty_proto```
request parameter) will change this default serialization behaviour
by adding a Layer Message for each requested Layer to the Protobuf even if none of the layers contains any features.
This workaround should be fine with current VectorTiles Specification.  

## Slippy Map Tiles Request

[Slippy Map Tiles](http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames) describes the tile format used by Google,
Bing and OSM. Usually a tile cache delivers the result to the client. But the **GeoWebCache** used by
the **Geoserver** does not allow to be extended with additional MimeTypes
(e.g. ```application/vnd.mapbox-vector-tile```) without a change to core classes. **Update:**
In GWC 1.8 the class will be extended and the protobuf MimeType will be supported.
Therefore a basic Slippy Map Tile Controller has been provided with this extension,
that translates the slippy map tile names into a WMS redirect. The Slippy Tile request has the
following format:

```
http://localhost/geoserver/slippymap/{layers}/{z}/{x}/{y}.{format}
```

 Path Variable | Description          | Type                                       
---------------|----------------------|--------------------------------------------
 *layers*      | layer or layer names | String or comma separated string list      
 *z*           | zoom level           | integer                                    
 *x*           | tile column          | integer                                    
 *y*           | tile row             | integer                                    
 *format*      | output format        | one of pbf,png,gif,jpg,tif,pdf,rss,kml,kmz 

To adjust the WMS parameters the following Request Parameters (in query String) are additionally allowed:

 Request Parameter       | Description                                                                                                                    | Type                                                     
-------------------------|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------
 *buffer*                | buffer size                                                                                                                    | integer default 10                                       
 *tileSize*              | size of result coordinate system (width and height)                                                                            | integer default 256                                      
 *styles*                | used styles for the layer(s)                                                                                                   | String or comma separated Strings if more than one layer 
 *time*                  | translated to WMS time parameter                                                                                               | Date String                                              
 *cql_filter*            | translated to WMS cql_filter parameter                                                                                         | String                                                   
 *viewparams*            | translated to WMS viewparams parameter                                                                                         | String                                                   
 *bboxToBoundsViewparam* | if set to true adds the WMS bounds as a viewparams value (can than be used e.g. to improve performance in Geoserver SQL Views) | Boolean                                                  
 *sld*                   | External Style Sheed Descriptor                                                                                                | URL (Location of SLD to be used)                         
 *sld_body*              | Style Description                                                                                                              | SLD XML                                                  
 *gen_level*             | generalisation level                                                                                                           | String (ENUM) Values LOW/MID/HIGH default MID            
 *gen_factor*            | generalisation factor                                                                                                          | Double default null                                      
 *small_geom_threshold*  | threshold for short lines / small areas, smaller ones then defined will be skipped in output                                   | Double default 0.05                                      
 *avoid_empty_proto*     | generate layer message in output even if no features are inclued to avoid 0 byte Protobufs                                     | Boolean default false 

### Example:

```
http://localhost/geoserver/slippymap/streets/13/4390/2854.pbf?buffer=5&styles=line
```

## Styling

Since the vector features are usualy styled on client side only the filter rules and not the symbolizers of a **GeoServer Style Sheet (SLD)
** are considered. Using scale denominators the filter rules can be adjusted so that for example some features are not delivered at higher
zoom levels. This prevents unnecessary delivery of features that are not rendered on the client anyway.

Following example shows an GeoServer Style Sheet (SLD) filtering features by a frc value.

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
                       xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
    <NamedLayer>
        <Name>test_filter</Name>
        <UserStyle>
            <Name>test</Name>
            <Title>Test Filter Style for Geoserver</Title>
            <Abstract>A sample style for pbf tiles that filters features according to its frc property</Abstract>
            <FeatureTypeStyle>
                <FeatureTypeName>Feature</FeatureTypeName>
                <Rule>
                    <MaxScaleDenominator>500000</MaxScaleDenominator>
                    <ogc:Filter>
                        <ogc:PropertyIsGreaterThanOrEqualTo>
                            <ogc:PropertyName>frc</ogc:PropertyName>
                            <ogc:Literal>3</ogc:Literal>
                        </ogc:PropertyIsGreaterThanOrEqualTo>
                    </ogc:Filter>
                </Rule>
                <Rule>
                    <MaxScaleDenominator>1000000</MaxScaleDenominator>
                    <ogc:Filter>
                        <ogc:PropertyIsEqualTo>
                            <ogc:PropertyName>frc</ogc:PropertyName>
                            <ogc:Literal>2</ogc:Literal>
                        </ogc:PropertyIsEqualTo>
                    </ogc:Filter>
                </Rule>
                <Rule>
                    <MaxScaleDenominator>2000000</MaxScaleDenominator>
                    <ogc:Filter>
                        <ogc:PropertyIsEqualTo>
                            <ogc:PropertyName>frc</ogc:PropertyName>
                            <ogc:Literal>1</ogc:Literal>
                        </ogc:PropertyIsEqualTo>
                    </ogc:Filter>
                </Rule>
                <Rule>
                    <ogc:Filter>
                        <ogc:PropertyIsEqualTo>
                            <ogc:PropertyName>frc</ogc:PropertyName>
                            <ogc:Literal>0</ogc:Literal>
                        </ogc:PropertyIsEqualTo>
                    </ogc:Filter>
                </Rule>
            </FeatureTypeStyle>
        </UserStyle>
    </NamedLayer>
</StyledLayerDescriptor>
```

In this example features with the property ```frc=0``` are never filtered. All other features containing a frc value are filtered if the
requested **ScaleDenominator** exceeds the defined **MaxScaleDenominator**.
