# Dissecting the Quake 2 BSP format

Taken from https://jheriko-rtw.blogspot.com/2010/11/dissecting-quake-2-bsp-format.html

There is already a pretty good documentation of the Quake 2 BSP format on flipcode. However, its lacking in the details
and it doesn't have enough information to load a BSP file and render it with light mapping or perform robust collision
detection against it. To work these details out I had to dive into the Quake 2 source code myself and dig these things
out... In case its useful to anyone else I thought I'd take the time to explain what GLToy does to load the files and
transform the data just enough to reproduce the environment complete with light maps and collision detection.

The less faint of heart could actually just take a look at the source code in GLToy - there is a single file dedicated
to loading the BSP v38 format, although its code gets invoked from inside a more general environment loading routine.
Looking at this you can probably see that I've done a bit of experimentation and bug fixing along the way - and that
actually I still haven't quite gotten the lightmaps right, although they appear to render correctly - and some of the
BSP structure is still a bit beyond me, the validation checks I have consistently fail when loading files produced by
the BSP compilers.

As I've hinted the story actually begins in another file - all this does is load the BSP header, check the version and
redirect to the appropriate routine. Being forward thinking and with a desire to complete the difficult task of single
handedly writing the "perfect" game engine I've already added stubs for the million and one flavours of BSP I know
about, as well as my own format which I haven't actually done anything with yet... I also use a general purpose bit
stream class which hides a lot of the fine detail behind overloaded operators, so the code is less than transparent.

The header starts with 4 bytes which identify the file type - in the case of Quake 2 files the header is equivalent to
the non-terminated string `IBSP` - this is also true of the other id BSP formats for other Quakes and Doom 3. Such a 4
character string is often referred to as a FOURCC (4 character code) and is quite a common way to allow a simple check
that the file you are loading is actually the type you are expecting. The next 4 bytes give us the version, and in the
case of Quake 2 this is the unsigned integer `38`. At this point GLToy_EnvironmentFile::LoadEnvironment decides which
loading routine to use based on the header and version, but in a loader just aiming to load a Quake 2 BSP you could just
check to make sure that the FOURCC and the version number are correct for the Quake 2 format, or even ignore the first 8
bytes if you are willing to assume the file is always going to be valid.

The next bit of file (we are now in GLToy_EnvironmentFile::LoadBSP38) is called the "lump directory", a lump is a block
of data in the BSP file and there are a whole load of different types. There are 19 entries in the Quake 2 flavour of
this structure (see GLToy_BSP38_LumpDirectory) and each entry includes an offset in the data where it can be found as
well as the size of the lump in bytes, both as 4-byte unsigned integers. The lumps I load in are described vaguely by
some constant definitions near the top of the file:

    static const u_int uBSP38_LUMP_ENTITIES = 0;
    static const u_int uBSP38_LUMP_PLANES = 1;
    static const u_int uBSP38_LUMP_VERTICES = 2;
    static const u_int uBSP38_LUMP_VIS = 3;
    static const u_int uBSP38_LUMP_NODES = 4;
    static const u_int uBSP38_LUMP_TEXTURES = 5;
    static const u_int uBSP38_LUMP_FACES = 6;
    static const u_int uBSP38_LUMP_LIGHTMAPS = 7;
    static const u_int uBSP38_LUMP_LEAVES = 8;
    static const u_int uBSP38_LUMP_LEAFFACETABLE = 9;
    static const u_int uBSP38_LUMP_LEAFBRUSHTABLE = 10;
    static const u_int uBSP38_LUMP_EDGES = 11;
    static const u_int uBSP38_LUMP_EDGEFACETABLE = 12;
    static const u_int uBSP38_LUMP_MODELS = 13;
    static const u_int uBSP38_LUMP_BRUSHES = 14;
    static const u_int uBSP38_LUMP_BRUSHSIDES = 15;

Lets take a look at each of these in more detail...

## Entities

The entities are stored as text in the same way that they are in the `.map` file the `.bsp` was generated from. This is a
bit of a pain, but if you have some good string manipulation routines then loading them isn't quite so painful. The
approach GLToy takes is to first split the string by the end braces `}`, trim trailing whitespace from the substrings
and remove the leading `{`. This leaves us one string per entity, each of which is then split by the double quote
character `"` to give a load of strings which contain the keys and values. The code then rearranges these into an array
of matched pairs of keys and values - checking that the number of keys and values actually matches and then passes the
data over to the entity system to create the actual entities. There are quite a large number of entities available in
the Quake 2 files, so I won't detail how each and everyone works - the simplest way to find this out is to create the
entity in a map file and inspect the contents yourself - aside from this GLToy doesn't load very many at the moment - I
think the only one which is tested is the spawn point.

## Planes

The planes are referred to by other structures to define the geometry of the environment and the spatial partitioning.
Each plane is 20 bytes, the first 12 define the normal as three floats, the next 4 is another float for the plane's
offset along the normal and the last 4 bytes describe the type of plane. Loading these all into an array is quite
straightforward, we divide the total lump size by 20 to get the number of planes then read the data from the offset in
the file. It is important to preserve the order in the array, since these are used to reference the planes in other
structures in the file.

## Vertices

The vertices are stored as 12 bytes each - three 32-bit floating point values, just like the normals for the planes.
These are also referred to from other structures and so these should be loaded in order, again using the offset and size
in the lump directory to initialise the array and load the data without overshooting the end.

## Visibility data

This lump is where it gets a bit more interesting and involved - it stores the potential visibility and hearability sets
and the clusters of BSP nodes used for the purposes of visibility culling (not drawing geometry you can't see) and sound
occlusion (not playing sounds that are blocked by geometry). The size in the lump directory will not be sufficient to
load the information this time - but the first 4 bytes in the vis lump gives us the number of clusters, and each cluster
is stored in this lump following this value as 8 bytes, 4 to identify the PVS (visibility set) and 4 for the PHS (
hearability set) - which should be loaded in as unsigned integers and treated as offsets inside of the visibility lump.
To simplify later steps GLToy loads the whole visibility lump into an array of unsigned chars using the offset and size
from the lump directory - this includes the 4 bytes for the number of clusters and the 8 byte values with the PVS and
PHS offsets.

I haven't actually used the PHS data yet (GLToy's sound support is still limited to something I added just to test that
I've initialised OpenAL properly[!]) but the PVS data is pretty important - without it even moderately sized
environments can be painfully slow to render (_Posted 8th November 2010_) - even a high-spec modern computer struggles
with the Quake 2 levels when
rendered naively. The PVS data is stored as a specially encoded array of bits, each bit representing whether a cluster
can be seen from another cluster, so there are the same number of bits in each entry as there are clusters in the PVS (
the first 4 byte value we loaded from the lump). Here is how GLToy decompresses them:

        // decompress the PVSs
        for( u_int w = 0; w < uNumClusters; ++w )
        {
            for( u_int u = 0, v = xVisOffsets[ w ].m_uPVS; u < uNumClusters; ++v )
            {
               if( xVisData[ v ] == 0 )
               {
                  ++v;     
                  u += 8 * xVisData[ v ];
               }
               else
               {
                  for( u_char ucBit = 1; ucBit != 0; ucBit <<= 1, ++u )
                  {
                     if( xVisData[ v ] & ucBit )
                     {
                        pxEnv->m_xClusters[ w ].m_xPVS.Append( u );
                     }
                  }
               }   
            }
        }

Notice that if a byte is zero it has a special behaviour, encoding the number of zeros that should be there - this helps
compress the mostly empty PVS. The way I've decompressed them creates an array of which clusters are visible for each
cluster - this is perhaps the most convenient form to have it in when it comes time to render the environment, although
we still don't know what the clusters actually are, but not to worry, each leaf node in the BSP tree is stored with its
parent cluster's index.

## BSP tree nodes

Next we come to the BSP tree nodes - the nodes are stored much like the planes and vertices, as a simple, flat array
with each entry taking 28 bytes. Unlike the planes and vertices though each node contains references to other data which
are needed to reconstruct the BSP tree. The first 4 bytes are the index of the plane used to split the node's volume -
this is a zero based index into the array of planes we've already loaded. This is then followed by two more 4-byte
values, these are signed indices - if positive they are a reference back into the node array, and if negative they are
an index into the array of leaves (we will come to this a bit later) which has been subtracted from -1 so that -1
represents the 0th index in the leaf array, -2 the 1st etc. After this we then have a bounding box for the node (which
allows us to construct an AABB tree for e.g. collision detection), these are encoded as 6 unsigned shorts (16-bit
values, so 12 bytes for each AABB), first the minimum extents, then the three maximum extents. We then have two further
unsigned shorts, one for the index of the first face in the node and the second for the total number of faces in the
node - we will load a face array a bit later which will give these values some more meaning.

Because that large block of text might be difficult to follow, here is the class used in GLToy, which (ignoring the
virtual function pointer) has the same layout as the nodes in the files.

    class GLToy_BSP38_Node
    : public GLToy_Serialisable
    {
    
    public:
    
        virtual void ReadFromBitStream( const GLToy_BitStream& xStream )
        {
            xStream >> m_uPlane;
            xStream >> m_iFrontChild;
            xStream >> m_iBackChild;
            xStream >> m_usBBMin[ 0 ];
            xStream >> m_usBBMin[ 1 ];
            xStream >> m_usBBMin[ 2 ];
            xStream >> m_usBBMax[ 0 ];
            xStream >> m_usBBMax[ 1 ];
            xStream >> m_usBBMax[ 2 ];
            xStream >> m_usFirstFace;
            xStream >> m_usFaceCount;
        }
    
        virtual void WriteToBitStream( GLToy_BitStream& xStream ) const
        {
            xStream << m_uPlane;
            xStream << m_iFrontChild;
            xStream << m_iBackChild;
            xStream << m_usBBMin[ 0 ];
            xStream << m_usBBMin[ 1 ];
            xStream << m_usBBMin[ 2 ];
            xStream << m_usBBMax[ 0 ];
            xStream << m_usBBMax[ 1 ];
            xStream << m_usBBMax[ 2 ];
            xStream << m_usFirstFace;
            xStream << m_usFaceCount;
        }
    
        u_int m_uPlane;
        int m_iFrontChild;
        int m_iBackChild;
        u_short m_usBBMin[ 3 ];
        u_short m_usBBMax[ 3 ];
        u_short m_usFirstFace;
        u_short m_usFaceCount;
    
    };

## Texture information

We then have a lump containing texture information, its another flat array where each element is 76 bytes. This
structure encodes a pair of basis vectors and offsets that allow transforming the vertex coordinates into texture
space - these are stored as 8 floating point values (32 bytes) at the start of the structure, the 3 components of the u
vector, the u offset then the v vector and the v offset. We then have 4 bytes of texture flags, a 4 byte value, a 32
byte string with the name of the texture and a final 4 byte value "next texture information". I haven't used either the
value or the next texture info in GLToy and don't know what they are for (D'oh).

## Faces

We then have the faces - these are the polygons which define the geometry. They are stored as a flat array in the lump,
with each one taking 20 bytes. Each face starts with an unsigned short which contains the index of the plane the face
lies on, the next value is another unsigned short for "the plane side" (unused by GLToy). We then have an unsigned int
containing an index into the edge array (we will come to this later) followed by an unsigned short with the number of
edges the face has. We then have an unsigned short which contains an index into the texture information array, 4 bytes
which represent light map styles (for animated light maps - GLToy doesn't use these) and a final 4 bytes which are an
offset for the face's light map data...

## Light maps

GLToy loads the whole of the light map data in one go as an array of unsigned characters. Making use of this data is
actually quite difficult without having loaded the faces and the vertices and having calculated the texture
coordinates (UVs) - each texture in this data starts at an offset from the face array, but the size of the texture is
derived from the texture coordinates at the vertices. I'll come back to these much later... knowing that you need to
load the data should do for now.

## BSP leaf nodes

We then have the leaf nodes for the BSP tree stored as a flat array with each element taking 28 bytes. The first 4 bytes
are some flags that GLToy doesn't use. We then have an unsigned short containing the index of the cluster to which the
leaf belongs, followed by another unsigned short containing, what I assume to be an index for the area to which the leaf
belongs (GLToy ignores this as well). We then have an AABB stored as 6 unsigned shorts, identical to how they are stored
in the non-leaf nodes. Following this we have an unsigned short for the index of the first leaf face, another for the
number of leaf faces, another for the index of the first leaf brush and another for the number of brushes in the leaf.

## Leaf face table

The leaf faces are stored in a flat array of unsigned shorts, each one is an index into the array of faces we discussed
earlier. This is so that each leafs face indices can be stored in sequence in this array, rather than storing them
directly in the face structure which would ruin the fixed size of the elements in the leaf array.

## Leaf brush table

The leaf brushes are stored in the same way as the leaf faces, a flat array of unsigned shorts which are indices into
the brush array. GLToy actually ignores this...

## Edges

The edges comprise another flat array, with each element being 4 bytes - an unsigned short for the index of the first
vertex on the edge and another for the second vertex.

## Edge face table

The edge face table is a flat array with 4 byte elements, each one being a signed integer value, with the sign being
used to indicate which order the vertices in the edge should be used.

## Models

The model lump is a flat array of 48 byte elements. Each element starts with a bounding box, but unlike the others we've
seen its stored as 6 32-bit floating point values, this is followed by an origin, stored as 3 floats. A 4-byte signed
integer stores the "head" - I can't remember what this is for (GLToy doesn't yet do anything with it). Following this
are a pair of unsigned ints, the first is the index of the first face, the second is the number of faces in the model.

## Brushes

The brushes are also stored in a flat array, this time with 12 byte elements. Each brush is stored as three unsigned
ints - the index of the first brush side (see the next lump), the number of brush sides and content flags for the brush.

## Brush sides

The brush sides comprise yet another flat array, this time each element is 4 bytes, an unsigned short containing the
index of the plane and a signed short for the texture information.

## Putting it all together

This is all the data we need to render/collide the environment in the way that GLToy does. There are other lumps and a
lot of the data loaded is ignored (for now, at least). Most of the information is quite easy to piece together into a
structure - the BSP nodes and leaves contain enough information to reassemble the BSP tree quite easily, but some of it
requires special care. The part I struggled with the most was the light map data - I tried implementing the method from
the Quake 2 source, but it failed for reasons I still don't fully understand, and the results were obviously out by a
texel in many cases, but correct for others. Anyway lets look at how the various pieces are put together by GLToy...

## Faces, vertices and texture coordinates

The faces are the first thing to be reconstructed from the data - in GLToy these are represented by the
GLToy_Environment_LightmappedFace. GLToy iterates over the face array that was loaded from the face lump. The lightmap
styles are copied, but are not yet used, then the texture information is looked up in the texture information array from
the index stored in the face array, the texture name is found and its hash stored in the
GLToy_Environment_LightmappedFace - this is specific to how GLToy deals with textures, you will want to do your own
thing to match whatever you are doing. Next it iterates over the face edges, for each one the vertex is looked up and
stored in the GLToy_Environment_Lightmapped's vertex array and the index stored in the
GLToy_Environment_LightmappedFace. Whilst doing this it also constructs the texture coordinates from the vectors and
offsets stored in the texture infos, it stores these unscaled in the GLToy_Environment_LightmappedFace's m_xLightmapUV,
although they will need adjusting later... the calculation is quite straightfoward and amounts to transforming the
vertex coordinates into the 2D texture space:

    pxEnv->m_xVertices[ uCurrentVertex ].m_xLightmapUV =
    GLToy_Vector_2(
    xTexInfos[ xBSPFace.m_usTextureInfo ].m_xUAxis * pxEnv->m_xVertices[ uCurrentVertex ].m_xPosition +
    xTexInfos[ xBSPFace.m_usTextureInfo ].m_fUOffset,
    xTexInfos[ xBSPFace.m_usTextureInfo ].m_xVAxis * pxEnv->m_xVertices[ uCurrentVertex ].m_xPosition +
    xTexInfos[ xBSPFace.m_usTextureInfo ].m_fVOffset );

The actual texture coordinates we need are dependent on the dimensions of the texture so in the m_xUV member the values
are copied, but divided by the width/height of the texture as necessary. This is important to consider if you want to
render the original Quake 2 levels at all - if you use higher resolution textures than the originals you will need to
either adjust the texture coordinates here, or later during rendering. GLToy does the later and provides a console
variable for toggling quad resolution textures since I found a rather convenient set of them which I used whilst testing
to make sure that everything came out correctly.

After this GLToy reorients the vertices to conform with its conventions for handedness and which axis represents the up
direction... this is optional, but depending on your renderer you might want to do the same, or at least something
similar.

## Light map textures

This is the step I had the most trouble with - after reading the description from flipcode and trying to recreate the
method used by the code in Quake 2 I had some problems - many of the textures seemed to be out by a single texel per row
which manifested as shadows cutting diagonally across some faces. The method I settled on seems to produce visually
correct results though.

There is a light map texture for each face, except for those with certain flags, so to construct the light maps GLToy
iterates over the faces, ignoring the ones with the sky flag (`0x4`), either of the transparency flags (`0x10`, `0x20`) or
the "no draw" flag (`0x80`). For the remaining faces we find the largest and smallest of each of the texture coordinate
components on the face, we then use these to get the width and height of the light map texture by taking the differences
with the most conservative rounding possible (the ceiling of the max and the floor of the min) - this is where I think
I've made a mistake since this differs from what I've seen in the Quake 2 source and on flipcode, also because some of
the widths exceed 16 - which is supposed to be the limit. However, the results are visually correct as far as I can
see...

Now that we have the size of the light map texture we go into the light map lump data we loaded earlier at the offset we
loaded in the face array, what we should find here is the texture stored as 24-bit RGB data, with the width and height
we just calculated, stored as rows. We then adjust the UVs we have stored in the GLToy_Environment_LightmappedFace
earlier so that they fit inside the lightmap minimally. This is how GLToy does it:

    xVertex.m_xLightmapUV[ 0 ] -= GLToy_Maths::Floor( fUMin / 16.0f ) * 16.0f;
    xVertex.m_xLightmapUV[ 0 ] += 8.0f;
    xVertex.m_xLightmapUV[ 0 ] /= uWidth * 16.0f;
    
    xVertex.m_xLightmapUV[ 1 ] -= GLToy_Maths::Floor( fVMin / 16.0f ) * 16.0f;
    xVertex.m_xLightmapUV[ 1 ] += 8.0f;
    xVertex.m_xLightmapUV[ 1 ] /= uHeight * 16.0f;

That's it for light maps - we now have the textures and the texture coordinates necessary to sample them correctly when
it comes time to render the environment.

## BSP tree and visibility clusters

In GLToy I actually decompress the PVS data quite late - but I've already described that, so the next thing that happens
that we haven't already discussed is reassembling the BSP tree and the visibility clusters. Iterating over the leaves we
add their indices to an array in a GLToy_Environment_LightmappedCluster - which simply wraps an array for the PVS and an
array for the indices of the leaves contained in the cluster.

GLToy has a data structure that represents a BSP tree already, so I reused that here which makes the next part a little
obscure in the code, but the idea is to create the nodes in an array from the data we got from the node and leaf lumps.
First the node entries from the node lump are iterated over and the planes are stored in a new GLToy_BSPNode, we then
iterate over the nodes again, setting the pointers in the GLToy_BSPNode based on the two indices in the lump's node
structure. If the indices are negative we create a new GLToy_BSPNode for the leaf based on the array in the leaf lump.

Once this is done there is a final call to initialise the GLToy_BSPTree that GLToy_Environment_Lightmapped is derived
from, then another to validate the tree. I should point out that the validation is never successful (I think this is
something to do with brush models), but that the result seems absolutely fine in game.

This is the point where GLToy decompresses the PVS - but that's not dependent on any of the previous work, so it is not
necessary to do it now, it can be done sooner or later with no problems.

## Brushes

The brushes are nice to have for collision detection - the faces are not so well suited, many of them are tiny and
difficult and my attempts to feed them into Havok resulted in an environment with small cracks you could fall through
and incredibly poor performance. Constructing them is fortunately very easy since each one is effectively a list of
planes, all GLToy does is iterate over the brush data from the brush lump and look up the planes at the indices stored
in the brush sides lump data, adding them to the brush in turn - these planes suffice to describe the brush. Some care
is taken to ignore certain brushes in this process - since I only use them for collision detection in GLToy (so far)
this makes sense, but it depends on what you want to do - for instance volume triggers rely on brush models which are
not going to block the player. Anyway, GLToy makes sure the brush has the solid flag (`0x1`), the player clip (`0x100000`)
or the AI clip (`0x200000`) flags before adding it to the list in the GLToy_Environment_Lightmapped.

After this GLToy loads the entities, but we've already discussed how this is handled. That's everything GLToy does, at
least when loading the BSP file. Now there is enough information in the GLToy_Environment_Lightmapped to easily render
the environment and perform collision detection.

There might be another post in future about how GLToy actually renders the environment, and if I ever get around to
writing my own collision detection, and physics which doesn't depend on the Havok library there might be one on
collision detection as well. For now though I think I'm done... 