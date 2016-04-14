/**
 * Copyright (c) 2014, by the Authors: John E Lloyd (UBC), Antonio Sanchez (UBC),
 * and ArtiSynth Team Members
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package maspack.render.GL;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.media.opengl.GL;
import javax.media.opengl.GL2GL3;
import javax.media.opengl.GL3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.event.MouseInputListener;

import maspack.matrix.AffineTransform2d;
import maspack.matrix.AffineTransform2dBase;
import maspack.matrix.AffineTransform3d;
import maspack.matrix.AffineTransform3dBase;
import maspack.matrix.AxisAlignedRotation;
import maspack.matrix.AxisAngle;
import maspack.matrix.Matrix3d;
import maspack.matrix.Matrix3dBase;
import maspack.matrix.Matrix4d;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform2d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.RotationMatrix3d;
import maspack.matrix.SVDecomposition3d;
import maspack.matrix.Vector2d;
import maspack.matrix.Vector3d;
import maspack.properties.HasProperties;
import maspack.properties.Property;
import maspack.properties.PropertyList;
import maspack.render.BumpMapProps;
import maspack.render.Dragger3d;
import maspack.render.Dragger3d.DraggerType;
import maspack.render.Dragger3dBase;
import maspack.render.DrawToolBase;
import maspack.render.IsRenderable;
import maspack.render.IsSelectable;
import maspack.render.Light;
import maspack.render.Material;
import maspack.render.MouseRayEvent;
import maspack.render.NormalMapProps;
import maspack.render.RenderList;
import maspack.render.RenderListener;
import maspack.render.RenderObject;
import maspack.render.RenderProps;
import maspack.render.RendererEvent;
import maspack.render.SortedRenderableList;
import maspack.render.TextureMapProps;
import maspack.render.Viewer;
import maspack.render.ViewerSelectionEvent;
import maspack.render.ViewerSelectionFilter;
import maspack.render.ViewerSelectionListener;
import maspack.render.GL.GLProgramInfo.RenderingMode;
import maspack.util.InternalErrorException;

/**
 * @author John E Lloyd and ArtiSynth team members
 */
public abstract class GLViewer implements GLEventListener, GLRenderer, 
   HasProperties, Viewer {

   public enum GLVersion {
      GL2, GL3
   }

   // matrices
   protected Matrix4d pickMatrix;
   protected Matrix4d projectionMatrix;
   protected RigidTransform3d viewMatrix;
   protected AffineTransform3dBase modelMatrix;
   protected Matrix3d modelNormalMatrix;            // inverse-transform (for normals)
   protected AffineTransform2dBase textureMatrix;   // transforming texture coordinates
   protected boolean modelMatrixValidP = false;
   protected boolean viewMatrixValidP = false;
   protected boolean projectionMatrixValidP = false;
   protected boolean textureMatrixValidP = false;
   // stacks 
   private LinkedList<Matrix4d> projectionMatrixStack;
   private LinkedList<RigidTransform3d> viewMatrixStack;
   private LinkedList<AffineTransform3dBase> modelMatrixStack;
   private LinkedList<Matrix3d> modelNormalMatrixStack;   // linked to model matrix
   private LinkedList<AffineTransform2dBase> textureMatrixStack;

   protected Vector3d zDir = new Vector3d();     // used for determining zOrder

   protected static final float DEFAULT_DEPTH_OFFSET_INTERVAL = 1e-5f; // prevent z-fighting
   private static final Point3d DEFAULT_VIEWER_CENTER = new Point3d();
   private static final Point3d DEFAULT_VIEWER_EYE = new Point3d (0, -1, 0);

   //protected static int DEFAULT_POINT_SLICES = 64;
   //protected static int DEFAULT_LINE_SLICES = 64;
   protected static int DEFAULT_SURFACE_RESOLUTION = 64;

   //protected int myPointSlices = DEFAULT_POINT_SLICES;
   //protected int myLineSlices = DEFAULT_LINE_SLICES;
   protected int mySurfaceResolution = DEFAULT_SURFACE_RESOLUTION; 

   // viewer state
   protected static class ViewState {
      protected Point3d myCenter = new Point3d (DEFAULT_VIEWER_CENTER);
      protected Point3d myUp = new Point3d (0, 0, 1);

      public ViewState clone() {
         ViewState vs = new ViewState();
         vs.myCenter.set(myCenter);
         vs.myUp.set(myUp);
         return vs;
      }
   }
   protected ViewState myViewState = null;
   protected LinkedList<ViewState> viewStateStack = null;
   
   protected static class ViewerState {
      public boolean lightingEnabled;       // light equations
      public boolean depthEnabled;          // depth buffer
      public boolean colorEnabled;          // color buffer
      public boolean vertexColorsEnabled;   // use per-vertex colors
      public boolean textureMappingEnabled; // use texture maps
      public FaceStyle faceMode;
      public Shading shading;
      public boolean hsvInterpolationEnabled;  
      public ColorMixing colorMixing;       // method for combining material/vertex colors
      public boolean roundedPoints;
      public boolean transparencyEnabled;
      public boolean transparencyFaceCulling;
      
      public ViewerState() {
         setDefaults();
      }
      
      public void setDefaults() {
         lightingEnabled = true;
         depthEnabled = true;
         colorEnabled = true;
         vertexColorsEnabled = true;
         textureMappingEnabled = true;
         faceMode = FaceStyle.FRONT;
         shading = Shading.SMOOTH;
         hsvInterpolationEnabled = false;
         colorMixing = ColorMixing.REPLACE;
         roundedPoints = true;
         transparencyEnabled = false;
         transparencyFaceCulling = false;
      }

      public ViewerState clone() {
         ViewerState c = new ViewerState();
         c.lightingEnabled = lightingEnabled;
         c.depthEnabled = depthEnabled;
         c.colorEnabled = colorEnabled;
         c.faceMode = faceMode;
         c.shading = shading;
         c.vertexColorsEnabled = vertexColorsEnabled;
         c.hsvInterpolationEnabled = hsvInterpolationEnabled;
         c.textureMappingEnabled = textureMappingEnabled;
         c.colorMixing = colorMixing;
         c.roundedPoints = roundedPoints;
         c.transparencyEnabled = transparencyEnabled;
         c.transparencyFaceCulling = transparencyFaceCulling;
         return c;
      }
   }
   protected ViewerState myViewerState;
   protected LinkedList<ViewerState> viewerStateStack;

   // state requests in case glContext not current
   boolean gammaCorrectionRequested = false;
   boolean gammaCorrectionEnabled = false;

   // frustum parameters
   protected static class ProjectionFrustrum {
      public double near = 1;
      public double far = 1000;
      public double left = -0.5;
      public double right = 0.5;
      public double top = 0.5;
      public double bottom = -0.5;

      public int depthBitOffset = 0;
      public int depthBits = 16;
      public double fov = 35;         // originally 70
      public double fieldHeight = 10; // originally 10
      public boolean orthographic = false;
      public boolean explicit = false;

      public ProjectionFrustrum clone() {
         ProjectionFrustrum c = new ProjectionFrustrum();
         c.near = near;
         c.far = far;
         c.left = left;
         c.right = right;
         c.top = top;
         c.bottom = bottom;
         c.depthBits = depthBits;
         c.depthBitOffset = depthBitOffset;
         c.fov = fov;
         c.fieldHeight = fieldHeight;
         c.orthographic = orthographic;
         c.explicit = explicit;
         return c;
      }
   }
   protected ProjectionFrustrum myFrustum = null;
   LinkedList<ProjectionFrustrum> frustrumStack = null;

   protected boolean resetViewVolume = false;

   public static final double AUTO_FIT = -1.0; // generic value to trigger an auto-fit

   // View transformations
   protected static final AxisAlignedRotation DEFAULT_AXIAL_VIEW =
      AxisAlignedRotation.X_Z;

   protected AxisAlignedRotation myDefaultAxialView = DEFAULT_AXIAL_VIEW;
   protected AxisAlignedRotation myAxialView = DEFAULT_AXIAL_VIEW;

   public enum RotationMode {
      DEFAULT, CONTINUOUS;
   }
   public static RotationMode DEFAULT_ROTATION_MODE = RotationMode.DEFAULT;
   protected RotationMode myRotationMode = DEFAULT_ROTATION_MODE;

   // enable or disable viewier re-scaling (disable when taking movie)
   protected boolean resizeEnabled = true;

   // program info
   protected GLLightManager lightManager = null;         
   protected GLProgramInfo myProgramInfo = null;    // controls for program to use
   
   // Colors
   protected static final Color DARK_RED = new Color (0.5f, 0, 0);
   protected static final Color DARK_GREEN = new Color (0, 0.5f, 0);
   protected static final Color DARK_BLUE = new Color (0, 0, 0.5f);
   
   protected float[] DEFAULT_MATERIAL_COLOR =    {0.8f, 0.8f, 0.8f, 1.0f};
   protected float[] DEFAULT_MATERIAL_EMISSION = {0.0f, 0.0f, 0.0f, 1.0f};
   protected float[] DEFAULT_MATERIAL_SPECULAR = {0.1f, 0.1f, 0.1f, 1.0f};
   protected float[] DEFAULT_HIGHLIGHT_COLOR =   {1f, 1f, 0f, 1f};
   protected float[] DEFAULT_SELECTING_COLOR =   {0f, 0f, 0f, 0f};
   protected float[] DEFAULT_BACKGROUND_COLOR =  {0f, 0f, 0f, 1f};
   protected float DEFAULT_MATERIAL_SHININESS = 32f;
   
   protected float[] myHighlightColor = Arrays.copyOf (DEFAULT_HIGHLIGHT_COLOR, 4);
   protected boolean myHighlightColorActive = false;
   protected HighlightStyle myHighlightStyle = HighlightStyle.COLOR;
   protected float[] mySelectingColor = Arrays.copyOf (DEFAULT_SELECTING_COLOR, 4); // color to use when selecting (color selection)
   
   protected Material myCurrentMaterial = Material.createDiffuse(DEFAULT_MATERIAL_COLOR, 32f);
   protected float[] myBackColor = null;
   protected boolean myCurrentMaterialModified = true;  // trigger for detecting when material is updated
   protected float[] backgroundColor = Arrays.copyOf (DEFAULT_BACKGROUND_COLOR, 4);
   
   // texture properties
   protected TextureMapProps myColorMapProps = null;
   protected NormalMapProps myNormalMapProps = null;
   protected BumpMapProps myBumpMapProps = null;
   
   // Canvas
   protected GLAutoDrawable drawable;  // currently active drawable
   protected GLCanvas canvas;          // main GL canvas
   
   protected int width;
   protected int height;

   // Generic Rendering
   /**
    * Class to set and hold rendering flags.  We enclose these in a class so
    * that we can access them in a synchronized way.
    */
   protected class RenderFlags {
      int myDefaultFlags = 0;
      int mySpecialFlags = 0;
      boolean mySpecialSet = false;

      /**
       * Accessed by application to set default rendering flags.
       */
      public void setDefault (int flags) {
         myDefaultFlags = flags;
      }

      /**
       * Accessed by application to request special flags for the next
       * render. Synchronized access is needed to set multiple values.
       */
      public synchronized void setSpecial (int flags) {
         // 
         mySpecialFlags = flags;
         mySpecialSet = true;
      }

      /**
       * Accessed by rendering code to get the appropriate flags for the
       * current cycle. Synchronized access needed to check and get two values.
       */
      public synchronized int get() {
         if (mySpecialSet) {
            mySpecialSet = false;
            return mySpecialFlags;
         }
         else {
            return myDefaultFlags;
         }
      }

      public boolean isSpecialSet() {
         return mySpecialSet;
      }
   }
   protected RenderFlags myRenderFlags = new RenderFlags();

   // list of renderables
   protected LinkedList<IsRenderable> myRenderables =  new LinkedList<IsRenderable>();
   protected boolean myInternalRenderListValid = false;
   protected RenderList myInternalRenderList = new RenderList();
   protected RenderList myExternalRenderList = null;

   // Renderable Objects and Tools
   protected LinkedList<Dragger3d> myDraggers;
   protected LinkedList<Dragger3d> myUserDraggers;
   protected MouseRayEvent myDraggerSelectionEvent;
   protected Dragger3d myDrawTool;
   protected Object myDrawToolSyncObject = new Object();
   protected Rectangle myDragBox;
   protected GLGridPlane myGrid;

   protected double axisLength = 0;
   protected boolean gridVisible = false;

   // Cut planes
   protected int maxClipPlanes = 6;                      // minimum 6 supported
   protected ArrayList<GLClipPlane> myClipPlanes = new ArrayList<GLClipPlane>(6);
   protected double[] myClipPlaneValues = new double[4]; // storing plane info

   // Interaction
   protected LinkedList<MouseInputListener> myMouseInputListeners = new LinkedList<MouseInputListener>();
   protected LinkedList<MouseWheelListener> myMouseWheelListeners = new LinkedList<MouseWheelListener>();
   protected GLMouseListener myMouseHandler;
   protected ArrayList<RenderListener> myRenderListeners =
   new ArrayList<RenderListener>();

   // Selection
   protected GLSelector mySelector;
   protected ViewerSelectionFilter mySelectionFilter = null;
   ViewerSelectionEvent selectionEvent;
   protected ArrayList<ViewerSelectionListener> mySelectionListeners = new ArrayList<ViewerSelectionListener>();
   protected boolean selectionEnabled = true;
   protected boolean selectOnPressP = false;   

   public static PropertyList myProps = new PropertyList (GLViewer.class);


   static {
      myProps.add (
         "eye", "eye location (world coordinates)", DEFAULT_VIEWER_EYE);
      myProps.add ("center", "center location (world coordinates)", DEFAULT_VIEWER_CENTER);
      myProps.add ("axisLength", "length of rendered x-y-z axes", 0);
      myProps.add("rotationMode", "method for interactive rotation", DEFAULT_ROTATION_MODE);
      myProps.add("axialView", "axis-aligned view orientation", DEFAULT_AXIAL_VIEW);
      myProps.add("defaultAxialView", "default axis-aligned view orientation", DEFAULT_AXIAL_VIEW);
      myProps.add ("backgroundColor", "background color", Color.BLACK);
      myProps.add("transparencyFaceCulling", "allow transparency face culling", false);
   }

   public PropertyList getAllPropertyInfo() {
      return myProps;
   }

   /**
    * {@inheritDoc}
    */
   public Property getProperty (String name) {
      return PropertyList.getProperty (name, this);
   }

   public int getSurfaceResolution () {
      return mySurfaceResolution;
   }
   
   public int setSurfaceResolution (int nres) {
      int prev = mySurfaceResolution;
      mySurfaceResolution = nres;
      return prev;
   }
   
   public void setAxisLength (double len) {
      if (len == AUTO_FIT) {
         Point3d pmin = new Point3d();
         Point3d pmax = new Point3d();
         getBounds (pmin, pmax);
         Vector3d vdiag = new Vector3d();
         vdiag.sub (pmax, pmin);
         axisLength = vdiag.norm() / 2;
      }
      else {
         axisLength = len;
      }
   }

   public double getAxisLength() {
      return axisLength;
   }

   public void setGridVisible (boolean visible) {
      gridVisible = visible;
   }

   public boolean getGridVisible() {
      return gridVisible;
   }

   public KeyListener[] getKeyListeners() {
      return getCanvas().getKeyListeners();
   }

   public void addKeyListener (KeyListener l) {
      getCanvas().addKeyListener(l);
   }

   public void removeKeyListener (KeyListener l) {
      getCanvas().removeKeyListener(l);
   }

   public LinkedList<Dragger3d> getDraggers() {
      // return all draggers except the internal clip plane dragger
      LinkedList<Dragger3d> list = new LinkedList<Dragger3d>();
      list.addAll (myUserDraggers);
      return list;
   }

   public void addSelectionListener (ViewerSelectionListener l) {
      mySelectionListeners.add (l);
   }

   public boolean removeSelectionListener (ViewerSelectionListener l) {
      return mySelectionListeners.remove (l);
   }

   public ViewerSelectionListener[] getSelectionListeners() {
      return mySelectionListeners.toArray (new ViewerSelectionListener[0]);
   }

   public ViewerSelectionEvent getSelectionEvent() {
      return selectionEvent;
   }

   protected void setSelected(List<LinkedList<?>> objs) {
      selectionEvent.setSelectedObjects (objs);
   }

   public void addRenderListener (RenderListener l) {
      myRenderListeners.add (l);
   }

   protected void fireRerenderListeners() {
      if (myRenderListeners.size() > 0) {
         RendererEvent e = new RendererEvent (this);
         for (RenderListener l : myRenderListeners) {
            l.renderOccurred (e);
         }
      }
   }

   public boolean removeRenderListener (RenderListener l) {
      return myRenderListeners.remove (l);
   }

   public RenderListener[] getRenderListeners() {
      return myRenderListeners.toArray (new RenderListener[0]);
   }

   public void addRenderable (IsRenderable d) {
      synchronized(myRenderables) {
         myRenderables.add (d);
      }
      myInternalRenderListValid = false;
   }

   public void addDragger (Dragger3d d) {
      synchronized(myDraggers) {
         myDraggers.add (d);
         myUserDraggers.add (d);
         if (d instanceof Dragger3dBase) {
            ((Dragger3dBase)d).setViewer (this);
         }
      }
      myInternalRenderListValid = false;
   }

   public void setDrawTool (Dragger3d d) {
      synchronized(myDrawToolSyncObject) {
         if (myDrawTool != d) {
            if (myDrawTool instanceof DrawToolBase) {
               ((DrawToolBase)myDrawTool).setViewer (null);
            }
            if (d instanceof DrawToolBase) {
               ((DrawToolBase)d).setViewer (this);
            }
            myDrawTool = d;
         }
      }
   }

   public boolean removeRenderable (IsRenderable d) {
      boolean wasRemoved = false;
      synchronized(myRenderables) {
         wasRemoved = myRenderables.remove (d);
      }
      myInternalRenderListValid = false;
      return wasRemoved;
   }

   public void removeDragger (Dragger3d d) {
      synchronized(myDraggers) {
         if (d instanceof Dragger3dBase) {
            ((Dragger3dBase)d).setViewer (null);
         }
         myDraggers.remove (d);
         myUserDraggers.remove (d);
      }
      myInternalRenderListValid = false;
   }

   public void clearDraggers() {
      synchronized(myDraggers) {
         for (Dragger3d d : myUserDraggers) {
            if (d instanceof Dragger3dBase) {
               ((Dragger3dBase)d).setViewer (null);
            }
            myDraggers.remove (d);
         }
         myUserDraggers.clear();
      }
      myInternalRenderListValid = false;
   }

   public void clearRenderables() {
      synchronized(myRenderables) {
         myRenderables.clear();
      }
      myInternalRenderListValid = false;
   }

   public double getViewPlaneDistance() {
      return myFrustum.near;
   }

   public double getFarPlaneDistance() {
      return myFrustum.far;
   }

   /**
    * Returns the default vertical field of view in degrees. This is used by
    * {@link #autoFitOrtho autoFitOrtho}.
    * 
    * @return default vertical field of view.
    */
   /*
    * This was removed from the above documentation because autoFitView looks
    * like it was removed {@link #autoFitView autoFitView} and
    */
   public double getVerticalFieldOfView() {
      return myFrustum.fov;
   }

   /**
    * Sets the default vertical field of view in degrees. This is used by
    * {@link #autoFitOrtho autoFitOrtho}.
    * 
    * @param fov
    * vertical field of view (degrees).
    */
   /*
    * This was removed from the above documentation because autoFitView looks
    * like it was removed {@link #autoFitView autoFitView} and
    */
   public void setVerticalFieldOfView (double fov) {
      myFrustum.fov = fov;
      computeProjectionMatrix ();
   }

   // for triggering selection process
   protected volatile boolean selectEnabled = false;
   protected volatile boolean selectTrigger = false;

   /**
    * Performs a selection operation on a sub-region of the viewport.
    * 
    * @param x
    * x coordinate of the selection region center
    * @param y
    * y coordinate of the selection region center
    * @param w
    * selection region width
    * @param h
    * selection region height
    * @param ignoreDepthTest
    * select all objects in the pick frustum, not just those which are
    * visible through the viewport
    */
   protected void setPick (double x, double y, double w, double h, boolean ignoreDepthTest) {

      if (ignoreDepthTest) {
         mySelector = new GLOcclusionSelector(this);
      }
      else {
         mySelector = new GLColorSelector(this);
      }
      mySelector.setRectangle (x, y, w, h);
      selectTrigger = true;
      repaint();
   }
   
   public void repaint() {
      if (!myInternalRenderListValid) {
         buildInternalRenderList();
      }
      if (canvas.isVisible()) {
         canvas.repaint();
      }

   }

   public void paint() {
      canvas.paint (canvas.getGraphics());
   }

   // not currently used
   protected void detachFromCanvas() {
      canvas.removeGLEventListener(this);
   }

   protected void buildInternalRenderList() {
      synchronized(myInternalRenderList) {
         synchronized (myRenderables) {
            myInternalRenderList.clear();
            myInternalRenderList.addIfVisibleAll (myRenderables);
            myInternalRenderListValid = true;
            // myInternalRenderList.addIfVisibleAll (myDraggers);  
         }
      }
   }

   public void setExternalRenderList (RenderList list) {
      myExternalRenderList = list;  
   }

   public RenderList getExternalRenderList() {
      return myExternalRenderList;
   }

   /**
    * Request a render with special flags that will be used
    * only for the duration of that render.
    */
   public void rerender(int flags) {
      buildInternalRenderList();
      myRenderFlags.setSpecial (flags);
      repaint();
   }

   /**
    * Used to see if rendering with special flags has been performed yet.
    */
   public boolean isSpecialRenderFlagsSet() {
      return myRenderFlags.isSpecialSet();
   }

   public void rerender() {
      buildInternalRenderList();
      repaint();
   }

   protected boolean isVisible() {
      return canvas.isVisible();
   }

   /**
    * Returns the bounds for the current frustum. These consist of the
    * quantities left, right, bottom, and top that describe the edge locations
    * of the near clipping plane (in eye coordinates), and near and far, which
    * describe the positions of the near and far clipping planes along the -z
    * axis (again in eye coordinates).
    * 
    * @param bounds
    * returns the values of left, right, bottom, top, near and far (in that
    * order)
    */
   public void getFrustum (double[] bounds) {
      if (bounds.length < 6) {
         throw new IllegalArgumentException (
         "bounds needs a length of at least 6");
      }
      bounds[0] = myFrustum.left;
      bounds[1] = myFrustum.right;
      bounds[2] = myFrustum.bottom;
      bounds[3] = myFrustum.top;
      bounds[4] = myFrustum.near;
      bounds[5] = myFrustum.far;
   }

   /**
    * Sets the viewing frustum to a general perspective projection.
    * 
    * @param left
    * left edge position of the near clipping plane
    * @param right
    * right edge position of the near clipping plane
    * @param bottom
    * bottom edge position of the near clipping plane
    * @param top
    * top position of the near clipping plane
    * @param near
    * near clipping plane position (along the -z axis; must be a positive
    * number)
    * @param far
    * far clipping plane position (along the -z axis; must be a positive number)
    */
   public void setPerspective (
      double left, double right, double bottom, double top, 
      double near, double far) {
      setPerspective(left,  right, bottom, top, near, far, true);
   }

   public void setPerspective (
      double left, double right, double bottom, double top, 
      double near, double far, boolean setExplicit) {
      this.myFrustum.left = left;
      this.myFrustum.right = right;
      this.myFrustum.bottom = bottom;
      this.myFrustum.top = top;
      this.myFrustum.near = near;
      this.myFrustum.far = far;
      this.myFrustum.explicit = setExplicit;
      this.myFrustum.orthographic = false;
      resetViewVolume = true;

      computeProjectionMatrix ();
   }

   /**
    * Sets the viewing frustum to a perspective projection centered about the -z
    * axis. Also sets the default field of view returned by
    * {@link #getVerticalFieldOfView getVerticalFieldOfView}.
    * 
    * @param fieldOfView
    * vertial field of view (in degrees)
    * @param near
    * near clipping plane position (along the -z axis; must be a positive
    * number)
    * @param far
    * far clipping plane position (along the -z axis; must be a positive number)
    */
   public void setPerspective (double fieldOfView, double near, double far) {
      double aspect = width / (double)height;

      this.myFrustum.top = near * Math.tan (Math.toRadians (fieldOfView) / 2);
      this.myFrustum.bottom = -this.myFrustum.top;
      this.myFrustum.left = -aspect * myFrustum.top;
      this.myFrustum.right = -aspect * myFrustum.bottom;
      this.myFrustum.near = near;
      this.myFrustum.far = far;

      this.myFrustum.fov = fieldOfView;
      this.myFrustum.explicit = false;
      this.myFrustum.orthographic = false;
      resetViewVolume = true;

      computeProjectionMatrix ();
   }

   /**
    * Sets the viewing frustum to an orthogonal projection centered about the -z
    * axis.
    * 
    * @param fieldHeight
    * vertical height of the field of view
    * @param near
    * near clipping plane position (along the -z axis; must be a positive
    * number)
    * @param far
    * far clipping plane position (along the -z axis; must be a positive number)
    */
   public void setOrthogonal (double fieldHeight, double near, double far) {
      double aspect = width / (double)height;

      this.myFrustum.top = fieldHeight / 2;
      this.myFrustum.bottom = -this.myFrustum.top;
      this.myFrustum.left = -aspect * myFrustum.top;
      this.myFrustum.right = -aspect * myFrustum.bottom;
      this.myFrustum.near = near;
      this.myFrustum.far = far;
      this.myFrustum.fieldHeight = fieldHeight;
      this.myFrustum.orthographic = true;
      resetViewVolume = true;

      computeProjectionMatrix ();
   }

   /**
    * Sets the viewing frustum to an orthogonal projection centered about the -z
    * axis.
    * 
    * @param fieldHeight
    * vertical height of the field of view
    * @param near
    * near clipping plane position (along the -z axis; must be a positive
    * number)
    * @param far
    * far clipping plane position (along the -z axis; must be a positive number)
    */
   protected void setOrthogonal2d (
      double left, double right, double bottom, double top) {

      setOrthogonal (left, right, bottom, top, -1, 1);
   }
 
   public void setOrthogonal (
      double left, double right, double bottom, double top, 
      double near, double far) {

      this.myFrustum.top = top;
      this.myFrustum.bottom = bottom;
      this.myFrustum.left = left;
      this.myFrustum.right = right;
      this.myFrustum.near = near;
      this.myFrustum.far = far;
      this.myFrustum.orthographic = true;
      this.myFrustum.fieldHeight = myFrustum.top-myFrustum.bottom;
      resetViewVolume = true;

      computeProjectionMatrix ();

   }

   /**
    * Returns true if the current viewing projection is orthogonal.
    * 
    * @return true if viewing projection is orthogonal
    */
   public boolean isOrthogonal() {
      return myFrustum.orthographic;
   }

   public void setOrthographicView (boolean enable) {
      if (enable) {
         autoFitOrtho();
      }
      else {
         autoFitPerspective();
      }
   }

   public boolean getBounds (Point3d pmin, Point3d pmax) {
      for (int i = 0; i < 3; i++) {
         pmin.set (i, Double.POSITIVE_INFINITY);
         pmax.set (i, Double.NEGATIVE_INFINITY);
      }
      boolean boundsSet = false;
      for (IsRenderable renderable : myRenderables) {
         renderable.updateBounds (pmin, pmax);
         boundsSet = true;
      }
      if (myExternalRenderList != null) {
         myExternalRenderList.updateBounds (pmin, pmax);
         boundsSet = true;
      }
      if (!boundsSet) {
         for (IsRenderable renderable : myDraggers) {
            renderable.updateBounds (pmin, pmax);
         }
      }
      if (pmin.x == Double.POSITIVE_INFINITY) { // then no bounds were set, so
         // use a default
         pmin.set (-1, -1, -1);
         pmax.set (1, 1, 1);
         return false;
      }
      else {
         return true;
      }
   }

   /**
    * Size is the diameter of the bounding box.
    */
   public double estimateRadiusAndCenter (Point3d center) {
      Point3d pmin = new Point3d();
      Point3d pmax = new Point3d();

      getBounds (pmin, pmax);
      if (center != null) {
         center.add (pmin, pmax);
         center.scale (0.5);
      }

      Vector3d vdiag = new Vector3d();
      vdiag.sub (pmax, pmin);
      double r = vdiag.norm() / 2;
      return r;
   }

   public void autoFit() {
      if (isOrthogonal()) {
         autoFitOrtho ();
      }
      else {
         autoFitPerspective (); // check if size is affected via autofit
      }
   }

   private boolean hasRenderables() {
      return (
      myRenderables.size() > 0 ||
      (myExternalRenderList != null && myExternalRenderList.size() > 0) ||
      myDraggers.size() > 0 || 
      myDrawTool != null);
   }

   private void setGridSizeAndPosition (Point3d pcenter, double r) {

      myGrid.setMinSize (4 * r);
      myGrid.setPosition (pcenter);
      myGrid.setAutoSized (true);
      // redajust grid position so that it aligns with the resolution
      double res = myGrid.getResolution().getMajorCellSize();
      double x = res*Math.round(pcenter.x/res);
      double y = res*Math.round(pcenter.y/res);
      double z = res*Math.round(pcenter.z/res);
      myGrid.setPosition (new Point3d(x, y, z));
   }

   /**
    * {@inheritDoc}
    */
   public void autoFitPerspective () {
      if (hasRenderables()) {
         Point3d pcenter = new Point3d();
         double r = estimateRadiusAndCenter (pcenter);
         //if radius is zero, set default to radius 1
         if ( Math.abs(r) == 0 || Double.isInfinite(r) || Double.isNaN(r)) {
            r = 1;
         }
         double far = 40 * r;
         double near = far / 1000;

         myViewState.myCenter.set (pcenter);
         Vector3d zdir = getEyeZDirection();
         double d = r / Math.tan (Math.toRadians (myFrustum.fov) / 2);
         Point3d eye = new Point3d();
         eye.scaledAdd(d, zdir, myViewState.myCenter);
         setEye(eye);

         setPerspective (myFrustum.fov, near, far);
         setGridSizeAndPosition (pcenter, r);

         if (isVisible()) {
            rerender();
         }
      }
   }

   /*
    * {@inheritDoc}
    */
   public void autoFitOrtho () {
      if (hasRenderables()) {
         Point3d pcenter = new Point3d();
         double r = estimateRadiusAndCenter (pcenter);

         //if radius is zero, set default to radius 1
         if ( Math.abs(r) == 0  || Double.isInfinite(r) || Double.isNaN(r)) {
            r = 1;
         }

         myViewState.myCenter.set (pcenter);
         Vector3d zdir = getEyeZDirection();
         double d = r / Math.tan (Math.toRadians (myFrustum.fov) / 2);
         Point3d eye = getEye();
         eye.scaledAdd(d, zdir, myViewState.myCenter);
         setEye(eye);

         double far = 40 * r;
         double near = far / 1000;
         setOrthogonal (2 * r, near, far);
         setGridSizeAndPosition (pcenter, r);

         if (isVisible()) {
            rerender();
         }
      }
   }

   public int getScreenWidth() {
      return width;
   }

   public int getScreenHeight() {
      return height;
   }

   public GL getGL() {
      if (drawable != null) {
         return drawable.getGL();
      }
      return null;
   }

   /**
    * Enable or disable the GL Canvas auto-swap mode
    * @param enable
    */
   public void setAutoSwapBufferMode (boolean enable) {
      canvas.setAutoSwapBufferMode (enable);
   }

   /**
    * Check whether or not the GL canvas is set to auto swap buffers
    */
   public boolean getAutoSwapBufferMode() {
      return canvas.getAutoSwapBufferMode();
   }

   /**
    * Gets the "currently active" context.  If not currently rendering,
    * this will return null;
    * @return active context
    */
   public GLContext getContext() {
      if (drawable != null) {
         return drawable.getContext();
      }
      return null;
   }

 
   //   /**
   //    * Distance of pixel from center (Euchlidean)
   //    * @param x
   //    * @param y
   //    * @return
   //    */
   //   private double centerDistance (int x, int y) {
   //      int dx = x - width / 2;
   //      int dy = y - height / 2;
   //      return Math.sqrt (dx * dx + dy * dy);
   //   }

   /**
    * Rotate the eye coordinate frame about the center point, independent
    * of the default up vector.
    * 
    * @param xang
    * amount of horizontal rotation (in radians)
    * @param yang
    * amount of vertical rotation (in radians)
    */
   protected void rotateContinuous (double xang, double yang) {

      Vector3d reye = new Vector3d();
      reye.sub (getEye(), myViewState.myCenter);

      Vector3d yCam = new Vector3d();     // up-facing vector
      Vector3d xCam = new Vector3d();     // right-facing vector

      synchronized(viewMatrix) {
         viewMatrix.R.getRow(1, yCam);
         viewMatrix.R.getRow(0, xCam);
      }

      //System.out.println("Transform: " + XEyeToWorld.R);
      if (yang != 0) {
         RotationMatrix3d R = new RotationMatrix3d(new AxisAngle(xCam, yang));
         reye.transform(R);
         yCam.transform(R);
      } 
      if (xang != 0) {
         reye.transform(new RotationMatrix3d(new AxisAngle(yCam, xang)));
      }
      Point3d eye = new Point3d();      
      eye.add (reye, myViewState.myCenter);
      setEyeToWorld (eye, myViewState.myCenter, yCam);

      repaint();
   }

   /**
    * Rotate the eye coordinate frame about the center point, while maintaining
    * the default up vector.
    * 
    * @param xang
    * amount of horizontal rotation (in radians)
    * @param yang
    * amount of vertical rotation (in radians)
    * @see #getUpVector
    */
   protected void rotateFixedUp (double xang, double yang) {
      Vector3d reye = new Vector3d();
      reye.sub (getEye(), myViewState.myCenter);

      if (yang != 0) {
         Vector3d xCam = new Vector3d(); // right-facing vector

         synchronized(viewMatrix) {
            viewMatrix.R.getRow (0, xCam);
         }

         double oldAngle = Math.acos (reye.dot (myViewState.myUp) / reye.norm());
         if (!((yang < 0 && (-yang) > oldAngle) ||
         (yang > 0 && yang > (Math.PI - oldAngle)))) {
            reye.transform (new RotationMatrix3d (new AxisAngle (xCam, yang)));
         } 

      }
      if (xang != 0) {
         reye.transform (new RotationMatrix3d (new AxisAngle (myViewState.myUp, xang)));
      }

      Point3d eye = new Point3d();
      eye.add (reye, myViewState.myCenter);

      setEyeToWorld (eye, myViewState.myCenter, myViewState.myUp);

      repaint();
   }

   /**
    * Rotate the eye coordinate frame about the center point
    * 
    * @param xang
    * amount of horizontal rotation (in radians)
    * @param yang
    * amount of vertical rotation (in radians)
    */
   protected void rotate (double xang, double yang) {

      switch (myRotationMode) {
         case CONTINUOUS:
            rotateContinuous(xang, yang);
            break;
         default:
            rotateFixedUp(xang, yang);
            break;
      }

   }

   /**
    * Translate the eye position with respect to the x-y plane of the eye frame.
    * The center point is translated by the same amount.
    * 
    * @param delx
    * x translation amount
    * @param dely
    * y translation amount
    */
   protected void translate (double delx, double dely) {
      Vector3d xCam = new Vector3d(), yCam = new Vector3d();

      synchronized (viewMatrix) {
         viewMatrix.R.getRow (0, xCam);
         viewMatrix.R.getRow (1, yCam);  
      }

      Vector3d offset = new Vector3d();
      offset.scale (-delx, xCam);
      offset.scaledAdd (-dely, yCam, offset);

      myViewState.myCenter.add (offset);
      Point3d eye = getEye();
      eye.add(offset);
      setEye(eye);

      repaint();
   }

   /**
    * Zoom in or out by a specified scale factor. A factor larger than one zooms
    * out, while a factor less than one zooms in. In orthographic projection,
    * zoom is accomplished changing the frustum size. In perspective projection,
    * it is accomplished by moving the eye position along the z axis of the eye
    * frame.
    * 
    * @param s
    * scale factor
    */
   public void zoom (double s) {
      if (myFrustum.orthographic) {
         myFrustum.fieldHeight *= s;
         resetViewVolume = true;
      }
      else {
         Vector3d reye = new Vector3d();
         Point3d eye = getEye();

         synchronized(viewMatrix) {
            reye.sub (eye, myViewState.myCenter);
            reye.transform(viewMatrix);
            reye.x = reye.y = 0;
            reye.inverseTransform (viewMatrix);
         }
         eye.scaledAdd (s - 1, reye);
         setEye(eye);
      }
      repaint();
   }

   /**
    * Computes the distance per pixel for a point specified with respect to
    * world coordinates.
    */
   public double distancePerPixel (Vector3d pnt) {
      if (myFrustum.orthographic) {
         return myFrustum.fieldHeight / height;
      }
      else {
         Point3d pntInEye = new Point3d (pnt);
         pntInEye.transform (viewMatrix);
         return Math.abs (pntInEye.z / myFrustum.near) * (myFrustum.top - myFrustum.bottom) / height;
      }
   }

   /**
    * Computes the distance per pixel at the viewpoint center.
    */
   public double centerDistancePerPixel() {
      return distancePerPixel (myViewState.myCenter);
   }

   private Color getAxisColor (int idx) {
      switch (idx) {
         case 0: {
            return DARK_RED;
         }
         case 1: {
            return DARK_GREEN;
         }
         case 2: {
            return DARK_BLUE;
         }
         default: {
            throw new InternalErrorException ("unknown index "+idx);
         }
      }
   }

   /**
    * Adjusts the orientation the eye frame with respect to world
    * coordinates. The grid is adjusted to align with the nearest set
    * of aligned axes. 
    * The distance between the center and the eye frame is unchanged.
    * 
    * @param REW
    * desired rotational transform from eye to world coordinates
    */
   protected void setAlignedEyeOrientation (RotationMatrix3d REW) {

      Vector3d xdir = new Vector3d();
      Vector3d ydir = new Vector3d();
      Vector3d zdir = new Vector3d();

      REW.getColumn (0, xdir);
      REW.getColumn (1, ydir);
      REW.getColumn (2, zdir);

      // new eye to world transfrom
      RigidTransform3d X = new RigidTransform3d();
      X.R.set (REW);
      double d = getEye().distance (myViewState.myCenter);
      X.p.scaledAdd (d, zdir, myViewState.myCenter);
      myViewState.myUp.set (ydir);
      setEyeToWorld (X);

      X.p.setZero();
      // adjust X.R to the nearest axis-aligned orientation
      int xmaxIdx = xdir.maxAbsIndex();
      double v = xdir.get(xmaxIdx) > 0 ? 1 : -1;
      xdir.setZero();
      xdir.set (xmaxIdx, v);

      ydir.set (xmaxIdx, 0);
      int ymaxIdx = ydir.maxAbsIndex();
      v = ydir.get(ymaxIdx) > 0 ? 1 : -1;
      ydir.setZero();
      ydir.set (ymaxIdx, v);

      X.R.setXYDirections (xdir, ydir);      
      myGrid.setGridToWorld (X);
      myGrid.setXAxisColor (getAxisColor (xmaxIdx));
      myGrid.setYAxisColor (getAxisColor (ymaxIdx));
   }

   public void setDefaultAxialView (AxisAlignedRotation view) {
      setAxialView (view);
      myDefaultAxialView = view;
   }

   public AxisAlignedRotation getDefaultAxialView () {
      return myDefaultAxialView;
   }

   /**
    * Sets an axial (or axis-aligned) view. This is done by setting the 
    * rotational part of the eye-to-world transform to the axis-aligned
    * rotation <code>REW</code>, and then moving the eye position so that 
    * the center position lies along the new -z axis of the eye frame, 
    * while maintaining the current distance between the eye and the center. 
    * 
    * <p>The method also sets this viewer's `up'' vector to the y axis of 
    * <code>REW</code>, and saves <code>REW</code> itself as the current
    * axis-aligned view, which can be retrieved using {@link #getAxialView}.
    * The viewer's grid is also adjusted to align with the nearest set
    * of aligned axes. 
    * 
    * @param REW axis-aligned rotational component for 
    * the eye-to-world transform
    * @see #getAxialView
    * @see #setUpVector
    * @see #getUpVector
    */
   public void setAxialView (AxisAlignedRotation REW) {
      setAlignedEyeOrientation (REW.getMatrix());
      myAxialView = REW;
   }

   /**
    * {@inheritDoc}
    */
   public AxisAlignedRotation getAxialView() {
      return myAxialView;
   }

   // end of the rotation code

   public GLViewer () {   

      myFrustum = new ProjectionFrustrum();
      frustrumStack = new LinkedList<>();

      myViewState = new ViewState();
      viewStateStack = new LinkedList<>();

      myViewerState = new ViewerState();
      viewerStateStack = new LinkedList<>();

      // initialize matrices
      projectionMatrix = new Matrix4d();
      viewMatrix = new RigidTransform3d();
      modelMatrix = new RigidTransform3d();
      modelNormalMatrix = new Matrix3d(modelMatrix.getMatrix());
      textureMatrix = RigidTransform2d.IDENTITY.clone();

      projectionMatrixStack = new LinkedList<>();
      viewMatrixStack = new LinkedList<>();
      modelMatrixStack = new LinkedList<>();
      modelNormalMatrixStack = new LinkedList<>();
      textureMatrixStack = new LinkedList<> ();

      computeProjectionMatrix ();
      invalidateModelMatrix();
      invalidateViewMatrix();
      invalidateProjectionMatrix();
      invalidateTextureMatrix ();
      
      myProgramInfo = new GLProgramInfo();
   }

   public GLCanvas getCanvas() {
      return canvas;
   }

   /**
    * Called any time GL context is switched! e.g. moving window to new display
    */
   public void init (GLAutoDrawable drawable) {
      GL gl = drawable.getGL ();
      String renderer = gl.glGetString(GL.GL_RENDERER);
      String version = gl.glGetString(GL.GL_VERSION);
      int[] buff = new int[2];
      gl.glGetIntegerv(GL3.GL_MAJOR_VERSION, buff, 0);
      gl.glGetIntegerv(GL3.GL_MINOR_VERSION, buff, 1);
      System.out.println("GL Renderer: " + renderer);
      System.out.println("OpenGL Version: " + version + " (" + buff[0] + "," + buff[1] + ")");
   }

   /**
    * Called any time GL context is switched! e.g. moving window to new display
    */
   public void dispose(GLAutoDrawable drawable) {
   }
   
   public void addMouseInputListener (MouseInputListener l) {
      if (canvas != null) {
         synchronized(canvas) {
            canvas.addMouseListener (l);
            canvas.addMouseMotionListener (l);
         }
      }
      myMouseInputListeners.add (l);
   }

   public void removeMouseInputListener (MouseInputListener l) {
      if (canvas != null) {
         synchronized(canvas) {
            canvas.removeMouseListener (l);
            canvas.removeMouseMotionListener (l);
         }
      }
      myMouseInputListeners.remove (l);
   }

   public MouseInputListener[] getMouseInputListeners() {
      return myMouseInputListeners.toArray (new MouseInputListener[0]);
   }

   public void addMouseWheelListener (MouseWheelListener l) {
      if (canvas != null) {
         synchronized(canvas) {
            canvas.addMouseWheelListener (l);
         }
      }
      myMouseWheelListeners.add (l);
   }

   public void removeMouseWheelListener (MouseWheelListener l) {
      if (canvas != null) {
         synchronized(canvas) {
            canvas.removeMouseWheelListener (l);
         }
      }
      myMouseWheelListeners.remove (l);
   }

   public MouseWheelListener[] getMouseWheelListeners() {
      return myMouseWheelListeners.toArray (new MouseWheelListener[0]);
   }

   public void reshape (GLAutoDrawable drawable, int x, int y, int w, int h) {
      this.drawable = drawable;
      GL gl = drawable.getGL ();
      
      width = w;
      height = h;

      // only resize view volume if not recording
      if (resizeEnabled) {
         resetViewVolume(gl);
      }
      repaint();
      
      this.drawable = null;
   }

   public double getViewPlaneHeight() {
      if (myFrustum.orthographic) {
         return myFrustum.fieldHeight;
      }
      else {
         return myFrustum.top - myFrustum.bottom;
      }
   }

   public double getViewPlaneWidth() {
      return (width / (double)height) * getViewPlaneHeight();
   }

   @Override
   public void setPointSize(float s) {
      setPointSize(getGL(), s);
   }
   
   protected void setPointSize(GL gl, float s) {
      if (gl instanceof GL2GL3) {
         GL2GL3 gl23 = (GL2GL3)gl;
         gl23.glPointSize(s);
      }
   }

   @Override
   public float getPointSize() {
      return getPointSize(getGL());
   }
   
   protected float getPointSize(GL gl) {
      if (gl instanceof GL2GL3) {
         GL2GL3 gl23 = (GL2GL3)gl;
         float[] buff = new float[1];
         gl23.glGetFloatv(GL.GL_POINT_SIZE, buff, 0);
         return buff[0];
      }
      return 0;
   }

   @Override
   public void setLineWidth(float w) {
      setLineWidth(getGL(), w);
   }
   
   protected void setLineWidth(GL gl, float w) {
      gl.glLineWidth (w);
   }

   public float getLineWidth() {
      return getLineWidth (getGL ());
   }

   protected float getLineWidth(GL gl) {
      float[] buff = new float[1];
      gl.glGetFloatv(GL.GL_LINE_WIDTH, buff, 0);
      return buff[0];
   }
   
   public void setViewport(GL gl, int x, int y, int width, int height) {
      gl.glViewport(x, y, width, height);
   }

   protected int[] getViewport(GL gl) {
      int[] buff = new int[4];
      gl.glGetIntegerv(GL.GL_VIEWPORT, buff, 0);
      return buff;
   }

   protected void resetViewVolume(GL gl) {
      resetViewVolume(gl, width, height);
   }

   protected void resetViewVolume(GL gl, int width, int height) {
      if (myFrustum.orthographic) {
         setOrthogonal(myFrustum.fieldHeight, myFrustum.near, myFrustum.far);
      }
      else {
         if (myFrustum.explicit) {
            setPerspective (
               myFrustum.left, myFrustum.right, myFrustum.bottom, myFrustum.top, 
               myFrustum.near, myFrustum.far);
         }
         else {
            double aspect = width / (double)height;
            myFrustum.left = -aspect * myFrustum.top;
            myFrustum.right = -myFrustum.left;
            setPerspective (
               myFrustum.left, myFrustum.right, myFrustum.bottom, myFrustum.top, 
               myFrustum.near, myFrustum.far, myFrustum.explicit);
         }
      }
      setViewport(gl, 0, 0, width, height);
   }

   // Sanchez, July 2013:
   // used to adjust selection volume, or else the orthogonal
   // view scale sometimes too large to properly detect selections
   protected void getZRange(Vector2d zRange) {

      if (!isOrthogonal()) {
         zRange.x = myFrustum.near;
         zRange.y = myFrustum.far;
         return;
      }

      Point3d pmin = new Point3d();
      Point3d pmax = new Point3d();
      getBounds(pmin, pmax);

      // find max z depth
      Vector3d zdir = getEyeZDirection();
      double worldDist = Math.abs(getEye().dot(zdir));
      double [] x = {pmin.x, pmax.x};
      double [] y = {pmin.y, pmax.y};
      double [] z = {pmin.z, pmax.z};
      double minz = Double.POSITIVE_INFINITY;
      double maxz = Double.NEGATIVE_INFINITY;
      for (int i=0; i<2; i++) {
         for (int j=0; j<2; j++) {
            for (int k=0; k<2; k++) {
               double d = x[i]*zdir.x + y[j]*zdir.y + z[k]*zdir.z;
               maxz = Math.max(maxz, d);
               minz = Math.min(minz, d);
            }
         }
      }

      // add 50% for good measure
      double d = maxz-minz;
      minz = minz-d/2;
      maxz = maxz+d/2;

      zRange.y = maxz + worldDist;
      zRange.x = 2*(minz + worldDist)-zRange.y;

   }

   public void setViewVolume (double near, double far) {
      if (myFrustum.orthographic) {
         setOrthogonal(myFrustum.fieldHeight, near, far);
      }
      else {
         if (myFrustum.explicit) {
            setPerspective(myFrustum.left, myFrustum.right, myFrustum.bottom, myFrustum.top, near, far);
         }
         else {
            double aspect = width / (double)height;
            myFrustum.left = -aspect * myFrustum.top;
            myFrustum.right = -myFrustum.left;
            setPerspective(myFrustum.left, myFrustum.right, myFrustum.bottom, myFrustum.top, near, far, myFrustum.explicit);
         }
      }
   }
   
   public void getViewMatrix (RigidTransform3d TWE) {
      TWE.set(viewMatrix);
   }
   
   public RigidTransform3d getViewMatrix () {
      return viewMatrix.clone();
   }

   public void getEyeToWorld (RigidTransform3d X) {
      X.invert(viewMatrix);
   }

   /**
    * Directly sets the eye coordinate frame.
    * 
    * @param X
    * new EyeToWorld transformation
    */
   public void setEyeToWorld (RigidTransform3d X) {
      viewMatrix.invert(X);
      // XXX
      if (Math.abs (viewMatrix.getOffset ().norm ()) < 1e-5) {
         // System.err.println ("bang"); Thread.dumpStack();
      }
      invalidateViewMatrix();
      repaint();
   }

   /**
    * Sets the eyeToWorld transform for this viewer, using the canonical
    * parameters used by the GL <code>lookat</code> method.
    * 
    * @param eye
    * position of the eye, in world coordinates
    * @param center
    * point that the eye is looking at, in world coordinates
    * @param up
    * up direction, in world coordinates
    */
   public void setEyeToWorld (Point3d eye, Point3d center, Vector3d up) {

      Vector3d zaxis = new Vector3d();
      Vector3d yaxis = new Vector3d();
      Vector3d xaxis = new Vector3d();
      zaxis.sub(eye, center);

      double n = zaxis.norm();
      if (n > 1e-12) {
         zaxis.scale(1.0/n);
      } else {
         RotationMatrix3d R = new RotationMatrix3d();
         R.rotateZDirection(up);
         R.getColumn(0, zaxis);
         R.getColumn(1, xaxis);
         R.getColumn(2, yaxis);
      }

      xaxis.cross(up, zaxis);
      n = xaxis.norm();
      if (n > 1e-6) {
         xaxis.scale(1.0/n);
         yaxis.cross(zaxis, xaxis);
         yaxis.normalize();
      } else {
         RotationMatrix3d R = new RotationMatrix3d();
         R.rotateZDirection(zaxis);
         R.getColumn(1, yaxis);
         R.getColumn(0, xaxis);
      }

      synchronized(viewMatrix) {
         viewMatrix.set(new double[]{
                                     xaxis.x, xaxis.y, xaxis.z, -xaxis.dot(eye),
                                     yaxis.x, yaxis.y, yaxis.z, -yaxis.dot(eye),
                                     zaxis.x, zaxis.y, zaxis.z, -zaxis.dot(eye),
         });
      }
      // XXX
      if (Math.abs (viewMatrix.getOffset ().norm ()) < 1e-5) {
         // System.err.println ("bang"); Thread.dumpStack();
      }

      invalidateViewMatrix();
   }

   /**
    * Add a depth offset to the projection matrix.
    * Each integer represents enough depth to account for one bin in the depth
    * buffer.  Negative values bring following objects closer to the screen.
    * This is to account for z-fighting.
    * 
    * @param zOffset value to offset depth buffer
    */
   public void addDepthOffset(double zOffset) {
      // compute depth buffer precision
      double deps = 2.0/(1 << (myFrustum.depthBits-1));
      // Let n and f be the far and near plane distances (positive values),
      // and z the z value in eye coordinates. Then the change in z 
      // corresponding to deps is
      //
      // delta z = - ((f-n) z^2)/(2 f n) deps
      //
      // We take z to be about 1/10 the distance to the far plane, which
      // corresponds roughly to where the center is when autoFit is used.
      Vector3d dp = new Vector3d();
      double f = myFrustum.far;
      double n = myFrustum.near;
      dp.scale (zOffset*(f-n)*f*deps/(2*n), getEyeZDirection());
      synchronized (modelMatrix) {
         modelMatrix.addTranslation (dp.x, dp.y, dp.z);
      }
      invalidateModelMatrix();
   }
   
//   public void setModelMatrix2d (double width, double height) {
//      setModelMatrix2d (0, width, 0, height);
//   }
 
   /**
    * {@inheritDoc}
    */
   public void setModelMatrix2d (
      double left, double right, double bottom, double top) {
      AffineTransform3d XMW = new AffineTransform3d();
      double w = right-left;
      double h = top-bottom;      
      XMW.A.m00 = 2/w;
      XMW.A.m11 = 2/h;
      XMW.p.set (-(left+right)/w, -(top+bottom)/h, 0);
      setModelMatrix (XMW);
   }

//   /**
//    * Set a depth offset to the projection matrix.
//    * Each integer represents enough depth to account for one bin in the depth
//    * buffer.  Negative values bring following objects closer to the screen.
//    * This is to account for z-fighting.
//    * @param offset value to offset depth buffer
//    */
//   public void setDepthOffset(int offset) {
//      myFrustum.depthBitOffset = offset;
//      computeProjectionMatrix ();
//   }

//   /**
//    * The current depth offset level.  Zero represents no offsets, 
//    * negative means objects are drawn closer to the viewer,
//    * positive means they are drawn further away.
//    * This is to account for z-fighting.
//    * @return the current offset (in depth bins)
//    */
//   public int getDepthOffset() {
//      return myFrustum.depthBitOffset;
//   }

   public void display (GLAutoDrawable drawable) {
      GLSupport.checkAndPrintGLError(drawable.getGL ());
      
      // assign current drawable
      this.drawable = drawable;

      int flags = myRenderFlags.get();

      // check if gamma property needs to be changed
      if (gammaCorrectionRequested) {
         GL gl = drawable.getGL ();
         if (gammaCorrectionEnabled) {
            gl.glEnable(GL2GL3.GL_FRAMEBUFFER_SRGB);
         } else {
            gl.glDisable(GL2GL3.GL_FRAMEBUFFER_SRGB);
         }
         gammaCorrectionRequested = false;
      }
      
      int depthBits = drawable.getChosenGLCapabilities ().getDepthBits ();
      if (depthBits != myFrustum.depthBits) {
         myFrustum.depthBits = depthBits;
         computeProjectionMatrix ();
      }

      display(drawable, flags);

      GLSupport.checkAndPrintGLError(drawable.getGL ());
      
      // clear current drawable
      this.drawable = null;
   }
   
   protected boolean hasTransparent3d() {
      if (myInternalRenderList.numTransparent() > 0) {
         return true;
      }
      if (myExternalRenderList != null) {
         if (myExternalRenderList.numTransparent() > 0) {
            return true;
         }
      }
      return false;
   }

   protected boolean has2d() {
      if (myInternalRenderList.numOpaque2d() > 0 ||
      myInternalRenderList.numTransparent2d() > 0) {
         return true;
      }
      if (myExternalRenderList != null) {
         if (myExternalRenderList.numOpaque2d() > 0 || 
         myExternalRenderList.numTransparent2d() > 0) {
            return true;
         }
      }
      return false;
   }

   protected boolean hasTransparent2d() {
      if (myInternalRenderList.numTransparent2d() > 0) {
         return true;
      }
      if (myExternalRenderList != null) {
         if (myExternalRenderList.numTransparent2d() > 0) {
            return true;
         }
      }
      return false;
   }

   private class RenderIterator implements Iterator<IsRenderable> {

      SortedRenderableList myList = null;
      int myListIdx = 0;
      int myIdx = -1;
      final int MAX_LIST_IDX = 7;

      public RenderIterator() {
         myList = myInternalRenderList.getOpaque();
         myIdx = -1;
         myListIdx = 0;
         advance();
      }

      public boolean hasNext() {
         return myList != null;
      }

      public IsRenderable next() {
         if (myList == null) {
            throw new NoSuchElementException();
         }
         IsRenderable r = myList.get(myIdx);
         advance();
         return r;
      }

      public void remove() {
         throw new UnsupportedOperationException();
      }

      private SortedRenderableList getList (int idx) {
         switch (idx) {
            case 0: {
               return myInternalRenderList.getOpaque();
            }
            case 1: {
               return (myExternalRenderList != null ?
                  myExternalRenderList.getOpaque() : null);
            }
            case 2: {
               return myInternalRenderList.getTransparent();
            }
            case 3: {
               return (myExternalRenderList != null ?
                  myExternalRenderList.getTransparent() : null);
            }
            case 4: {
               return myInternalRenderList.getOpaque2d();
            }
            case 5: {
               return (myExternalRenderList != null ?
                  myExternalRenderList.getOpaque2d() : null);
            }
            case 6: {
               return myInternalRenderList.getTransparent2d();
            }
            case 7: {
               return (myExternalRenderList != null ?
                  myExternalRenderList.getTransparent2d() : null);
            }
            default: {
               throw new ArrayIndexOutOfBoundsException ("idx=" + idx);
            }
         }
      }               

      private void advance() {
         myIdx++;
         if (myIdx >= myList.size()) {
            myIdx = 0;
            myList = null;
            while (++myListIdx <= MAX_LIST_IDX) {
               SortedRenderableList l = getList (myListIdx);
               if (l != null && l.size() > 0) {
                  myList = l;
                  break;
               }
            }
         }
      }
   }

   protected Iterator<IsRenderable> renderIterator() {
      return new RenderIterator();
   }

   public abstract void display (GLAutoDrawable drawable, int flags);

   public void setBackgroundColor (float r, float g, float b) {
      setBackgroundColor(r, g, b, 1.0f);
   }

   protected void setBackgroundColor(float r, float g, float b, float a) {
      backgroundColor[0] = r;
      backgroundColor[1] = g;
      backgroundColor[2] = b;
      backgroundColor[3] = a;
      repaint();
   }
   
   public void setBackgroundColor (float[] rgba) {
      if (rgba.length < 3) {
         throw new IllegalArgumentException ("rgba must have length >= 3");
      }
      float alpha = rgba.length > 3 ? rgba[3] : 1f;
      setBackgroundColor (rgba[0], rgba[1], rgba[2], alpha);
   }

   public void setBackgroundColor (Color color) {
      color.getComponents (backgroundColor);
      repaint();
   }

   public Color getBackgroundColor() {
      return new Color (backgroundColor[0], backgroundColor[1], backgroundColor[2], backgroundColor[3]);
   }

   public float[] getBackgroundColor(float[] rgba) {
      if (rgba == null) {
         rgba = new float[4];
      }
      for (int i=0; i<rgba.length; ++i) {
         rgba[i] = backgroundColor[i];
      }
      return rgba;
   }

   public void setDefaultLights() {

      //      // For debugging lights, set to R-G-B
      //      float light0_ambient[] = { 0.2f, 0.2f, 0.2f, 1.0f };
      //      float light0_diffuse[] = { 0.8f, 0.0f, 0.0f, 1.0f };
      //      float light0_specular[] = { 0, 0, 0, 1 };
      //      float light0_position[] = { 1, 0, 0, 0 };
      //      
      //      float light1_ambient[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      //      float light1_diffuse[] = { 0.0f, 0.8f, 0.0f, 1.0f };
      //      float light1_specular[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      //      float light1_position[] = { 0, 1, 0, 0 };
      //
      //      float light2_ambient[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      //      float light2_diffuse[] = { 0.0f, 0.0f, 0.8f, 1.0f };
      //      float light2_specular[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      //      float light2_position[] = { 0, 0, 1, 0 };
      
      float light0_ambient[] = { 0.1f, 0.1f, 0.1f, 1f };
      float light0_diffuse[] = { 0.8f, 0.8f, 0.8f, 1.0f };
      float light0_specular[] = { 0.5f, 0.5f, 0.5f, 1.0f };
      float light0_position[] = { -0.8660254f, 0.5f, 1f, 0f };

      float light1_ambient[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      float light1_diffuse[] = { 0.5f, 0.5f, 0.5f, 1.0f };
      float light1_specular[] = { 0.5f, 0.5f, 0.5f, 1.0f };
      float light1_position[] = { 0.8660254f, 0.5f, 1f, 0f };

      float light2_ambient[] = { 0.0f, 0.0f, 0.0f, 1.0f };
      float light2_diffuse[] = { 0.5f, 0.5f, 0.5f, 1.0f };
      float light2_specular[] = { 0.5f, 0.5f, 0.5f, 1.0f };
      float light2_position[] = { 0f, -10f, 1f, 0f };

      lightManager.clearLights();
      lightManager.addLight(new Light (
         light0_position, light0_ambient, light0_diffuse, light0_specular));
      lightManager.addLight (new Light (
         light1_position, light1_ambient, light1_diffuse, light1_specular));
      lightManager.addLight(new Light (
         light2_position, light2_ambient, light2_diffuse, light2_specular));
      lightManager.setMaxIntensity(1.0f);
      
      myProgramInfo.setNumLights (lightManager.numLights ());
   }
   
   public boolean setLightingEnabled (boolean enable) {
      boolean prev = myViewerState.lightingEnabled;
      if (enable != prev) {
         myViewerState.lightingEnabled = enable;
         if (enable) {
            myProgramInfo.setShading (Shading.NONE);
         } else {
            myProgramInfo.setShading (getShading());
         }
      }
      
      return prev;
   }

   public boolean isLightingEnabled() {
      return myViewerState.lightingEnabled && myViewerState.shading != Shading.NONE;
   }

   public boolean setVertexColoringEnabled (boolean enable) {
      boolean prev = myViewerState.vertexColorsEnabled;
      myViewerState.vertexColorsEnabled = enable;
      return prev;
   }

   public boolean isVertexColoringEnabled() {
      return myViewerState.vertexColorsEnabled;
   }
   
   public boolean hasVertexColoring(){
      return (myViewerState.vertexColorsEnabled && myViewerState.colorMixing != ColorMixing.NONE); 
   }

   protected boolean isHSVColorInterpolationEnabled() {
      return myViewerState.hsvInterpolationEnabled;
   }

   public ColorInterpolation getColorInterpolation() {
      if (myViewerState.hsvInterpolationEnabled) {
         return ColorInterpolation.HSV;
      }
      else {
         return ColorInterpolation.RGB;
      }
   }

   /**
    * Sets the color interpolation method to be used
    * @param interp new color interpolation
    * @return  the previous value so it can be reset
    */
   public ColorInterpolation setColorInterpolation (ColorInterpolation interp) {
      ColorInterpolation prev = getColorInterpolation();
      myViewerState.hsvInterpolationEnabled = (interp==ColorInterpolation.HSV);
      
      myProgramInfo.setColorInterpolation (interp);
      
      return prev;
   }

   protected void setHSVCColorInterpolationEnabled(boolean set) {
      setColorInterpolation (ColorInterpolation.HSV);
   }

   public boolean setTextureMappingEnabled (boolean enable) {
      boolean prev = myViewerState.textureMappingEnabled; 
      myViewerState.textureMappingEnabled = enable;
      return prev;
   }

   public boolean isTextureMappingEnabled() {
      return myViewerState.textureMappingEnabled;
   }
   
   public abstract boolean hasVertexColorMixing (ColorMixing cmix);
   
   @Override
   public ColorMixing setVertexColorMixing (ColorMixing cmix) {
      ColorMixing prev = myViewerState.colorMixing;
      if (hasVertexColorMixing(cmix)) {
         myViewerState.colorMixing = cmix;
         
         myProgramInfo.setVertexColorMixing (cmix);
         
      }
      return prev;
   }
   
   @Override
   public ColorMixing getVertexColorMixing () {
      return myViewerState.colorMixing;
   }
   
   public abstract boolean hasTextureMixing (ColorMixing tmix);
   
   @Override
   public TextureMapProps setTextureMapProps (TextureMapProps props) {
      TextureMapProps old = myColorMapProps;
      if (hasTextureMapping()) {
         if (props != null) {
            myColorMapProps = props.clone();
         } else {
            myColorMapProps = null;
         }
      }
      return old;
   }
   
   @Override
   public NormalMapProps setNormalMapProps (NormalMapProps props) {
      NormalMapProps old = myNormalMapProps;
      if (hasNormalMapping()){
         if (props != null) {
            myNormalMapProps = props.clone();
         } else {
            myNormalMapProps = null;
         }
      }
      return old;
   }
   
   @Override
   public BumpMapProps setBumpMapProps (BumpMapProps props) {
      BumpMapProps old = myBumpMapProps;
      if (hasBumpMapping()) {
         if (props != null) {
            myBumpMapProps = props.clone();
         } else {
            myBumpMapProps = null;
         }
      }
      return old;
   }

   protected boolean isGammaCorrectionEnabled() {
      return gammaCorrectionEnabled;
   }

   protected void setGammaCorrectionEnabled(boolean set) {
      gammaCorrectionRequested = true;
      repaint();
   }

   public boolean setDepthEnabled(boolean set) {
      boolean prev = myViewerState.depthEnabled;
      myViewerState.depthEnabled = set;
      return prev;
   }

   protected boolean isDepthEnabled() {
      return myViewerState.depthEnabled;
   }

   protected boolean isColorEnabled() {
      return myViewerState.colorEnabled;
   }

   protected void setColorEnabled(boolean enable) {
      myViewerState.colorEnabled = enable;
   }

   public Shading setShading(Shading shading) {
      Shading prev = myViewerState.shading;
      myViewerState.shading = shading;
      
      if (isLightingEnabled ()) {
         myProgramInfo.setShading (shading);
      }
      
      return prev;
   }

   public Shading getShading() {
      return myViewerState.shading;
   }
   
   protected void pushViewerState() {
      viewerStateStack.push(myViewerState.clone());
   }
   
   public boolean setRoundedPoints(boolean enable) {
      boolean prev = myViewerState.roundedPoints;
      myViewerState.roundedPoints = enable;
      
      myProgramInfo.setRoundPointsEnabled (enable);
      
      return prev;
   }
   
   public boolean getRoundedPoints() {
      return myViewerState.roundedPoints;
   }

   protected void popViewerState() {
      setViewerState(viewerStateStack.pop());
   }

   protected void setViewerState(ViewerState state) {  
      setLightingEnabled(state.lightingEnabled);
      setDepthEnabled(state.depthEnabled);
      setColorEnabled(state.colorEnabled);
      setVertexColoringEnabled(state.vertexColorsEnabled);
      setTextureMappingEnabled(state.textureMappingEnabled);
      setFaceStyle(state.faceMode);
      setShading(state.shading);
      setHSVCColorInterpolationEnabled(state.hsvInterpolationEnabled);
      setVertexColorMixing (state.colorMixing);
      setRoundedPoints(state.roundedPoints);
      setTransparencyEnabled (state.transparencyEnabled);
      setTransparencyFaceCulling (state.transparencyFaceCulling);
   }

   public boolean isTransparencyEnabled() {
      return myViewerState.transparencyEnabled;
   }

   public void setTransparencyEnabled (boolean enable) {
      myViewerState.transparencyEnabled = enable;
   }

   /*
    * set "up" vector for viewing matrix
    */
   public void setUpVector (Vector3d upVector) {
      myViewState.myUp.set (upVector);
      setEyeToWorld (getEye(), myViewState.myCenter, myViewState.myUp);
   }

   public Vector3d getUpVector() {
      return myViewState.myUp;
   }

   /**
    * {@inheritDoc}
    */
   public boolean hasSelection() {
      return true;
   }
   
   /**
    * {@inheritDoc}
    */
   public boolean isSelecting() {
      return selectEnabled;
   }

   public boolean setSelectionHighlightStyle (HighlightStyle style) {
      if (style == HighlightStyle.NONE) {
         // turn off highlighting if currently selected
         if (myHighlightColorActive) {
            myHighlightColorActive = false;
            // indicate that we may need to update color state
            myCurrentMaterialModified = true;
         }
      }
      switch (style) {
         case NONE:
         case COLOR: {
            myHighlightStyle = style;
            return true;
         }
         default:
            return false;
      }
   }
   
   public boolean hasSelectionHighlightStyle (HighlightStyle style) {
      switch (style) {
         case NONE:
         case COLOR: {
            return true;
         }
         default:
            return false;
      }
   }

   public HighlightStyle getSelectionHighlightStyle() {
      return myHighlightStyle;
   }

   public void setHighlightColor (Color color) {
      color.getRGBComponents (myHighlightColor);
   }

   @Override
   public void getHighlightColor (float[] rgba) {
      if (rgba.length < 3) {
         throw new IllegalArgumentException (
            "Argument rgba must have length of at least 3");
      }
      rgba[0] = myHighlightColor[0];
      rgba[1] = myHighlightColor[1];
      rgba[2] = myHighlightColor[2];
      if (rgba.length > 3) {
         rgba[3] = myHighlightColor[3];
      }
   }

   public void setSelectingColor (Color color) {
      color.getRGBComponents (mySelectingColor);
      myCurrentMaterialModified = true;
   }

   /**
    * The material color to use if the renderer is currently performing a selection
    * render. This is mainly used for color-based selection.
    * 
    * @param r red
    * @param g green
    * @param b blue
    * @param a alpha
    */
    public void setSelectingColor(float r, float g, float b, float a) {
      mySelectingColor[0] = r;
      mySelectingColor[1] = g;
      mySelectingColor[2] = b;
      mySelectingColor[3] = a;
      myCurrentMaterialModified = true;
   }

   public void getSelectingColor (float[] rgba) {
      if (rgba.length < 3) {
         throw new IllegalArgumentException (
            "Argument rgba must have length of at least 3");
      }
      rgba[0] = mySelectingColor[0];
      rgba[1] = mySelectingColor[1];
      rgba[2] = mySelectingColor[2];
      if (rgba.length > 3) {
         rgba[3] = mySelectingColor[3];
      }
   }
   
   @Override
   public FaceStyle setFaceStyle(FaceStyle style) {
      FaceStyle prev = myViewerState.faceMode;
      myViewerState.faceMode = style;
      return prev;
   }

   @Override
   public FaceStyle getFaceStyle () {
      return myViewerState.faceMode;
   }

   public void setSelectionEnabled (boolean selection) {
      selectionEnabled = selection;
   }

   public boolean isSelectionEnabled() {
      return selectionEnabled;
   }

   public void setSelectOnPress (boolean enable) {
      selectOnPressP = enable;
   }

   public boolean getSelectOnPress() {
      return selectOnPressP;
   }

   public Point3d getCenter() {
      return new Point3d (myViewState.myCenter);
   }

   /**
    * Sets the center point for the viewer, and adjusts the eye coordinates so
    * that the eye's -z axis is directed at the center point. The vertical
    * direction is specified by the current up vector.
    * 
    * @param c
    * new center location, in world coordinates
    * @see #getUpVector
    */
   public void setCenter (Point3d c) {
      myViewState.myCenter.set (c);
      setEyeToWorld (getEye(), myViewState.myCenter, myViewState.myUp);
   }

   public Point3d getEye() {
      Point3d eye = new Point3d();
      eye.inverseTransform(viewMatrix.R, viewMatrix.p);
      eye.negate();
      return eye;
   }

   /**
    * Moves the eye coordinates to a specifed location in world coordinates, and
    * adjusts the orientation so that the eye's -z axis is directed at the
    * center point. The vertical direction is specified by the current up
    * vector.
    * 
    * @param eye
    * new eye location, in world coordinates
    * @see #getCenter
    * @see #getUpVector
    */
   public void setEye (Point3d eye) {
      // eye.set (e);
      setEyeToWorld (eye, myViewState.myCenter, myViewState.myUp);
   }

   /**
    * Returns a transform from world coordinates to center coordinates, with the
    * axes aligned to match the current eyeToWorld transform. Seen through the
    * viewer, this will appear centered on the view frame with z pointing toward
    * the view, y pointing up, and x pointing to the right.
    */
   public RigidTransform3d getCenterToWorld() {
      RigidTransform3d X = new RigidTransform3d();
      synchronized (viewMatrix) {
         X.R.transpose(viewMatrix.R);  
      }
      X.p.set (myViewState.myCenter);
      return X;
   }

   protected void setDragBox (Rectangle box) {
      myDragBox = box;
   }

   protected Rectangle getDragBox() {
      return myDragBox;
   }

   public int getCellDivisions() {
      return myGrid.getCellDivisions();
   }

   public double getCellSize() {
      return myGrid.getCellSize();
   }

   public GLGridPlane getGrid() {
      return myGrid;
   }

   public Light addLight (
      float[] position, float[] ambient, float[] diffuse, float[] specular) {
      Light light = new Light (position, ambient, diffuse, specular);
      lightManager.addLight (light);
      myProgramInfo.setNumLights (lightManager.numLights ());
      return light;
   }
   
   public int addLight (Light light) {
      int idx = lightManager.numLights();
      lightManager.addLight (light);
      myProgramInfo.setNumLights (idx+1);
      return idx;
   }

   public void removeLight (int i) {
      if (lightManager.removeLight(i)) {
         myProgramInfo.setNumLights (lightManager.numLights ());
      }
   }

   public boolean removeLight (Light light) {
      if (lightManager.removeLight(light)) {
         myProgramInfo.setNumLights (lightManager.numLights ());
         return true;
      }
      else {
         return false;
      }
   }

   public Light getLight (int i) {
      return lightManager.getLight (i);
   }
   
   public int getIndexOfLight (Light light) {
      return lightManager.indexOfLight (light);
   }

   public int numLights() {
      return lightManager.numLights();
   }

   /**
    * Setup for a screenshot during the next render cycle
    * @param w width of shot
    * @param h height of shot
    * @param samples number of samples to use for the
    *        multisample FBO (does antialiasing)
    * @param file
    * @param format
    */
   public abstract void setupScreenShot (
      int w, int h, int samples, File file, String format);

   public abstract void awaitScreenShotCompletion();

   /**
    * Allows you explicitly enable or disable resizing of viewer
    * (may want to disable while recording video)
    */
   public void setResizeEnabled(boolean enabled) {
      resizeEnabled = enabled;
   }

   public boolean isResizeEnabled() {
      return resizeEnabled;
   }

   public abstract void setupScreenShot (
      int w, int h, File file, String format);

   public abstract boolean grabPending();

   public void setRotationMode (RotationMode mode) {
      myRotationMode = mode;
   }

   public RotationMode getRotationMode() {
      return myRotationMode;
   }

   public boolean setTransparencyFaceCulling(boolean enable) {
      boolean prev = myViewerState.transparencyFaceCulling;
      myViewerState.transparencyFaceCulling = enable;
      return prev;
   }

   public boolean getTransparencyFaceCulling() {
      return myViewerState.transparencyFaceCulling;
   }

   public Vector3d getEyeZDirection() {
      Vector3d zdir = new Vector3d();
      viewMatrix.R.getRow(2, zdir);
      return zdir;
   }

   public void setGlobalRenderFlags(int flags) {
      myRenderFlags.setDefault (flags);
   }

   public int getRenderFlags () {
      return myRenderFlags.get(); 
   }

   public boolean begin2DRendering() {
      return begin2DRendering (getScreenWidth(), getScreenHeight());
   }
   
   public boolean has2DRendering() {
      return true;
   }
   
   public void begin2DRendering (
      double left, double right, double bottom, double top) {

      // save depth, lighting, face culling information
      pushViewerState();
      pushProjectionMatrix();
      pushViewMatrix();
      pushModelMatrix();
      pushTextureMatrix ();
      
      setOrthogonal2d(-1, 1, -1, 1);
      setViewMatrix(RigidTransform3d.IDENTITY);
      setModelMatrix2d (left, right, bottom, top);
      
      setDepthEnabled (false);
      setLightingEnabled (false);
      setFaceStyle (FaceStyle.FRONT_AND_BACK);
      //setModelMatrix(RigidTransform3d.IDENTITY);

      rendering2d = true;
   }

   public void finish2DRendering() {

      popTextureMatrix ();
      popModelMatrix();
      popViewMatrix();
      popProjectionMatrix();
      popViewerState();

      rendering2d = false;
   }

   @Override
   public boolean begin2DRendering (double w, double h) {
      if (rendering2d) {
         throw new IllegalStateException ("Already in 2D rendering mode");
      }     
      if (has2DRendering()) {
         begin2DRendering(0, w, 0, h);
         return true;
      }
      else {
         return false;
      }
   }

   @Override
   public void end2DRendering() {
      if (!rendering2d) {
         throw new IllegalStateException ("Not in 2D rendering mode");
      }     
      if (has2DRendering()) {
         finish2DRendering();
      }
   }

   @Override
   public boolean is2DRendering() {
      return rendering2d;
   }

   protected int numSelectionQueriesNeeded() {
      int num = myInternalRenderList.numSelectionQueriesNeeded();
      if (myExternalRenderList != null) {
         num += myExternalRenderList.numSelectionQueriesNeeded();
      }
      return num;
   }

   public void beginSelectionQuery (int idx) {
      if (selectEnabled) {
         mySelector.beginSelectionQuery (idx);
      }
   }

   public void endSelectionQuery () {
      if (selectEnabled) {
         mySelector.endSelectionQuery ();
      }
   }

   public void beginSubSelection (IsSelectable s, int idx) {
      if (selectEnabled) {
         mySelector.beginSelectionForObject (s, idx);
      }
   }

   public void endSubSelection () {
      if (selectEnabled) {
         mySelector.endSelectionForObject ();
      }
   }

   public void setSelectionFilter (ViewerSelectionFilter filter) {
      mySelectionFilter = filter;
   }

   public ViewerSelectionFilter getSelectionFilter () {
      return mySelectionFilter;
   }

   public boolean isSelectable (IsSelectable s) {
      if (s.isSelectable()) {
         if (s.numSelectionQueriesNeeded() < 0 && mySelectionFilter != null) {
            return mySelectionFilter.isSelectable(s);
         }
         return true;
      }
      else {
         return false;
      }
   }

   public GLMouseListener getMouseHandler() {
      return myMouseHandler;
   }

   public void setMouseHandler(GLMouseListener handler) {

      if (myMouseHandler != null) { 
         canvas.removeMouseListener(myMouseHandler);
         canvas.removeMouseWheelListener (myMouseHandler);
         canvas.removeMouseMotionListener (myMouseHandler);
      }

      myMouseHandler = handler;

      canvas.addMouseListener(myMouseHandler);
      canvas.addMouseWheelListener (myMouseHandler);
      canvas.addMouseMotionListener (myMouseHandler);

   }

   public abstract void cleanupScreenShots();

   protected void setRenderingProgramMode(RenderingMode mode) {
      myProgramInfo.setMode (mode);
   }
   
   protected void computeProjectionMatrix() {

      // from frustrum info
      double[] pvals = null;
      double w = myFrustum.right-myFrustum.left;
      double h = myFrustum.top-myFrustum.bottom;
      double d = myFrustum.far-myFrustum.near;
      
      
      // adjust offset to account for proper bin depth
      double zoffset = 0;
      if (myFrustum.depthBitOffset != 0) {
         // XXX should be 2, but doesn't seem to work well... 512 works better?
         zoffset = 2.0*myFrustum.depthBitOffset/(1 << (myFrustum.depthBits-1));
      }
      
      if (myFrustum.orthographic) {
         pvals = new double[]{
                              2/w, 0, 0, -(myFrustum.right+myFrustum.left)/w,
                              0, 2/h, 0, -(myFrustum.top+myFrustum.bottom)/h,
                              0,0,-2/d, -(myFrustum.far+myFrustum.near)/d+zoffset,
                              0, 0, 0, 1
         };
      } else {
         pvals = new double[] {
                               2*myFrustum.near/w, 0, (myFrustum.right+myFrustum.left)/w, 0,
                               0, 2*myFrustum.near/h, (myFrustum.top+myFrustum.bottom)/h, 0,
                               0, 0, -(myFrustum.far+myFrustum.near)/d-zoffset, -2*myFrustum.near*myFrustum.far/d,
                               0, 0, -1, 0
         };
      }


      if (pickMatrix != null) {
         Matrix4d p = new Matrix4d(pvals);
         projectionMatrix.mul(pickMatrix, p);

         //         System.out.println("Pick projection:");
         //         System.out.println(projectionMatrix);
      } else {
         projectionMatrix.set(pvals);
         //         System.out.println("Projection:");
         //         System.out.println(projectionMatrix);
      }

      invalidateProjectionMatrix();
   }

   /**
    * Alternative to gluPickMatrix, pre-multiplies by appropriate matrix to
    * reduce size
    */
   protected void setPickMatrix(float x, float y, float deltax, float deltay, int[] viewport) {
      // pre-multiply by pick
      if (deltax <= 0 || deltay <= 0) { 
         return;
      }
      // projectionMatrix.setIdentity();

      pickMatrix = new Matrix4d();

      // scale
      pickMatrix.set(0, 0, viewport[2]/deltax);
      pickMatrix.set(1, 1, viewport[3]/deltay);
      pickMatrix.set(2, 2, 1);

      // translate
      pickMatrix.set(0, 3, (viewport[2] - 2 * (x - viewport[0])) / deltax);
      pickMatrix.set(1, 3, (viewport[3] - 2 * (y - viewport[1])) / deltay);
      pickMatrix.set(2, 3, 0);
      pickMatrix.set(3,3, 1);

      // pre-multiply
      projectionMatrix.mul(pickMatrix, projectionMatrix);

      invalidateProjectionMatrix ();

   }

   // used internally for selection
   public void clearPickMatrix() {
      pickMatrix = null;
      computeProjectionMatrix (); // recompute projection
   }

   public void setModelMatrix(AffineTransform3dBase m) {
      synchronized(modelMatrix) {
         if (modelMatrix.getClass() == m.getClass()) {
            modelMatrix.set(m);
         } else {
            modelMatrix = m.clone();
         }
         modelNormalMatrix = computeInverseTranspose(m.getMatrix());
      }
      invalidateModelMatrix();
   }

   public void setViewMatrix(RigidTransform3d v) {
      synchronized(viewMatrix) {
         viewMatrix.set(v);
      }
      // XXX
      if (Math.abs (viewMatrix.getOffset ().norm ()) < 1e-5) {
         // System.err.println ("bang"); Thread.dumpStack();
      }
      invalidateViewMatrix();
   }

   public void getModelMatrix(AffineTransform3d m) {
      m.set(modelMatrix);
   }
   
   public AffineTransform3dBase getModelMatrix() {
      return modelMatrix.clone();
   }

   protected void resetModelMatrix() {
      synchronized (modelMatrix) {
         modelMatrix = new RigidTransform3d(); // reset to identity
         modelNormalMatrix = new Matrix3d(modelMatrix.getMatrix());   
      }
      invalidateModelMatrix();
   }

   protected boolean isModelMatrixRigid() {
      return (modelMatrix instanceof RigidTransform3d);
   }

   public void translateModelMatrix(Vector3d t) {
      translateModelMatrix (t.x, t.y, t.z);
      invalidateModelMatrix();
   }

   public void translateModelMatrix(double tx, double ty, double tz) {
      RigidTransform3d TR = new RigidTransform3d (tx, ty, tz);
      mulModelMatrix (TR);
   }

   public void rotateModelMatrix(double zdeg, double ydeg, double xdeg) {
      RigidTransform3d TR = new RigidTransform3d (
         0, 0, 0, 
         Math.toRadians(zdeg), Math.toRadians(ydeg), Math.toRadians(xdeg)); 
      mulModelMatrix (TR);
   }

   public void scaleModelMatrix(double s) {
      synchronized(modelMatrix) {
         AffineTransform3d am = new AffineTransform3d(modelMatrix);
         am.applyScaling(s, s, s);
         modelMatrix = am; // normal matrix is unchanged
      }
      invalidateModelMatrix();
   }

   public void scaleModelMatrix(double sx, double sy, double sz) {
      synchronized(modelMatrix) {
         AffineTransform3d am = new AffineTransform3d(modelMatrix);
         am.applyScaling(sx, sy, sz);
         modelMatrix = am;
         if (sx == 0) {
            sx = Double.MAX_VALUE;
         } else {
            sx = 1.0/sx;
         }
         if (sy == 0) {
            sy = Double.MAX_VALUE;
         } else {
            sy = 1.0/sy;
         }
         if (sz == 0) {
            sz = Double.MAX_VALUE;
         } else {
            sz = 1.0/sz;
         }
         modelNormalMatrix.scaleColumn(0, sx);
         modelNormalMatrix.scaleColumn(1, sy);
         modelNormalMatrix.scaleColumn(2, sz);
      }
      invalidateModelMatrix();
   }

   public void mulModelMatrix (AffineTransform3dBase trans) {
      synchronized(modelMatrix) {
         if (trans instanceof RigidTransform3d) {
            RigidTransform3d rigid = (RigidTransform3d)trans;
            modelMatrix.mul(rigid);
            modelNormalMatrix.mul(rigid.R);  
         }
         else {
            AffineTransform3d aff = new AffineTransform3d(modelMatrix);
            aff.mul(trans);
            modelMatrix = aff;
            modelNormalMatrix = computeInverseTranspose(aff.getMatrix());
         }
      }
      invalidateModelMatrix();
   }

   private void setFromTransform(Matrix4d X, AffineTransform3dBase T) {
      X.setSubMatrix(0, 0, T.getMatrix());
      Vector3d b = T.getOffset();
      X.m03 = b.x;
      X.m13 = b.y;
      X.m23 = b.z;
      X.m30 = 0;
      X.m31 = 0;
      X.m32 = 0;
      X.m33 = 1;
   }

   public void getModelMatrix (Matrix4d X) {
      setFromTransform(X, modelMatrix);
   }

   public void getViewMatrix (Matrix4d X) {
      setFromTransform(X, viewMatrix);
   }

   public void getProjectionMatrix (Matrix4d X) {
      X.set(projectionMatrix);
   }
   
   public Matrix4d getProjectionMatrix() {
      return projectionMatrix.clone ();
   }
   
   @Override
   public void setTextureMatrix(AffineTransform2dBase trans) {
      synchronized(textureMatrix) {
         if (textureMatrix.getClass() == trans.getClass()) {
            textureMatrix.set(trans);
         } else {
            textureMatrix = trans.clone();
         }
      }
      invalidateTextureMatrix();
   }
   
   public AffineTransform2dBase getTextureMatrix() {
      return textureMatrix.clone ();
   }
   
   public void getTextureMatrix (AffineTransform2d X) {
      X.set (textureMatrix);
   }

   protected void invalidateModelMatrix() {
      modelMatrixValidP = false;
   }

   protected void invalidateProjectionMatrix() {
      projectionMatrixValidP = false;
   }

   protected void invalidateViewMatrix() {
      viewMatrixValidP = false;
   }
   
   protected void invalidateTextureMatrix() {
      textureMatrixValidP = false;
   }

   public void pushViewMatrix() {
      viewMatrixStack.push(viewMatrix.clone());
      viewStateStack.push(myViewState);
   }

   public boolean popViewMatrix() {
      if (viewMatrixStack.size() == 0) {
         return false;
      } 
      viewMatrix = viewMatrixStack.pop();
      myViewState = viewStateStack.pop();
      
      // XXX
      if (Math.abs (viewMatrix.getOffset ().norm ()) < 1e-5) {
         // System.err.println ("bang"); Thread.dumpStack();
      }
      
      invalidateViewMatrix();
      return true;
   }

   public void pushModelMatrix() {
      modelMatrixStack.push(modelMatrix.clone());
      modelNormalMatrixStack.push(modelNormalMatrix.clone());
   }

   protected Matrix3d computeInverseTranspose(Matrix3dBase M) {
      Matrix3d out = new Matrix3d(M);
      if (!(M instanceof RotationMatrix3d)) {
         boolean success = out.invert();
         if (!success) {
            SVDecomposition3d svd3 = new SVDecomposition3d(M);
            svd3.pseudoInverse(out);
         }
         out.transpose();
      }
      return out;
   }

   public boolean popModelMatrix() {
      if (modelMatrixStack.size() == 0) {
         return false;
      } 
      modelMatrix = modelMatrixStack.pop();
      modelNormalMatrix = modelNormalMatrixStack.pop();
      invalidateModelMatrix();
      return true;
   }

   public void pushProjectionMatrix() {
      projectionMatrixStack.push(projectionMatrix.clone());
      frustrumStack.push(myFrustum.clone());
   }

   public boolean popProjectionMatrix() {
      if (projectionMatrixStack.size() == 0) {
         return false;
      } 
      projectionMatrix = projectionMatrixStack.pop();
      myFrustum = frustrumStack.pop();
      invalidateProjectionMatrix();
      return true;
   }
   
   public void pushTextureMatrix() {
      textureMatrixStack.push (textureMatrix.clone ());
   }
   
   public boolean popTextureMatrix() {
      if (textureMatrixStack.size () == 0) {
         return false;
      }
      textureMatrix = textureMatrixStack.pop ();
      invalidateTextureMatrix ();
      return true;
   }

   //==========================================================================
   //  Clip Planes
   //==========================================================================

   protected int numUsedClipPlanes() {
      int c = 0;
      for (GLClipPlane clip : myClipPlanes) {
         if (clip.isClippingEnabled()) {
            c++;
            if (clip.isSlicingEnabled()) {
               c++;
            }
         }
      }
      return c;
   }
   
   public int numFreeClipPlanes() {
      int c = numUsedClipPlanes ();
      if (c >= maxClipPlanes) {
         return 0;
      }
      return maxClipPlanes-c;
   }

   public int getMaxGLClipPlanes() {
      return maxClipPlanes;
   }

   public GLClipPlane addClipPlane () {
      return addClipPlane (null, 0);
   }

   public GLClipPlane addClipPlane (RigidTransform3d X, double size) {
      GLClipPlane clipPlane = new GLClipPlane();
      if (size <= 0) {
         size = centerDistancePerPixel()*getScreenWidth()/2;
      }
      if (X == null) {
         X = getCenterToWorld();
      }
      clipPlane.setMinSize (size);
      clipPlane.setGridToWorld (X);
      clipPlane.setOffset (size / 50.0);
      clipPlane.setGridVisible (true);
      clipPlane.setMinCellPixels (8);
      clipPlane.setDragger (DraggerType.Transrotator);

      addClipPlane (clipPlane);
      return clipPlane;
   }

   public boolean addClipPlane (GLClipPlane clipPlane) {
      clipPlane.setViewer (this);
      myClipPlanes.add (clipPlane);
      
      if (isVisible()) {
         rerender();
      }
      return true;
   }

   public GLClipPlane getClipPlane (int idx) {
      return myClipPlanes.get (idx);
   }

   public int getNumClipPlanes() {
      return myClipPlanes.size();
   }

   public GLClipPlane[] getClipPlanes () {
      return myClipPlanes.toArray (new GLClipPlane[0]);
   }

   public boolean removeClipPlane (GLClipPlane clipPlane) {
      if (myClipPlanes.remove (clipPlane)) {
         clipPlane.setViewer (null);
         if (isVisible()) {
            rerender();
         }
         return true;
      }
      else {
         return false;
      }
   }

   public void clearClipPlanes() {
      for (GLClipPlane clip : myClipPlanes) {
         clip.setViewer(null);
      }
      myClipPlanes.clear();
      if (isVisible()) {
         rerender();
      }
      myProgramInfo.setNumClipPlanes (0);
   }

   private float[] toFloat (Vector3d vec) {
      return new float[] {(float)vec.x, (float)vec.y, (float)vec.z};
   }
   
   //==========================================================================
   //  Drawing
   //==========================================================================

   public void drawSphere (Vector3d pnt, double rad) {
      drawSphere (toFloat(pnt), rad);
   }

   @Override
   public void drawCube (Vector3d pnt, double w) {
      drawCube(toFloat(pnt), w);
   }
   
   @Override
   public void drawBox (Vector3d pnt, Vector3d widths) {
      drawBox (toFloat(pnt), widths.x, widths.y, widths.z);
   }
   
   public void drawCylinder (
      Vector3d pnt0, Vector3d pnt1, double rad, boolean capped) {
      drawCylinder (toFloat(pnt0), toFloat(pnt1), rad, capped);
   }
   
//   public void drawCylinder (
//      float[] pnt0, float[] pnt1, double rad) {
//      drawCylinder (pnt0, pnt1, rad, /* capped= */false);
//   }
//
//   public void drawArrow (
//      RenderProps props, float[] coords0, float[] coords1) {
//      drawArrow (props, coords0, coords1, 0, /* capped= */true);
//   }

   public void drawLine (
      RenderProps props, float[] pnt0, float[] pnt1, boolean selected) {
      drawLine (props, pnt0, pnt1, /*color=*/null, /*capped=*/true, selected);
   }

//   public void drawLine (
//      RenderProps props, float[] coords0, float[] coords1, boolean capped,
//      boolean selected) {
//      drawLine (props, coords0, coords1, capped, null, selected);
//   }

//   public abstract void drawCone (
//      float[] pnt0, float[] pnt1, double rad0, double rad1, boolean capped);

//   public void drawCone(
//      RenderProps props, float[] coords0, float[] coords1) {
//      drawCone(props, coords0, coords1, false);
//   }

   public void drawPoint (Vector3d pnt) {
      drawPoint (new float[] {(float)pnt.x, (float)pnt.y, (float)pnt.z});
   }

   public void drawPoint (double px, double py, double pz) {
      drawPoint (new float[] {(float)px, (float)py, (float)pz});
   }

//   public void drawPoint (float x, float y, float z) {
//      drawPoint (new float[] {x, y, z});
//   }
//
   public void drawLine (Vector3d pnt0, Vector3d pnt1) {
      drawLine (toFloat (pnt0), toFloat (pnt1));
   }

   public void drawLine (
      double px0, double py0, double pz0, double px1, double py1, double pz1) {
      drawLine (
         new float[] {(float)px0, (float)py0, (float)pz0}, 
         new float[] {(float)px1, (float)py1, (float)pz1}); 
   }

//   public void drawLine (
//      float x0, float y0, float z0, float x1, float y1, float z1) {
//      drawLine (new float[] {x0, y0, z0}, new float[] {x1, y1, z1});
//   }
//   
   public void drawTriangle (Vector3d pnt0, Vector3d pnt1, Vector3d pnt2) {
      drawTriangle (toFloat(pnt0), toFloat(pnt1), toFloat(pnt2));
   }

//   public abstract void drawLines(float[] vertices, int flags);

//   public void drawLines(float[] vertices) {
//      drawLines(vertices, 0);
//   }
//
   protected void computeNormal(float[] p0, float[] p1, float[] p2, float[] normal) {
      float[] u = new float[3];
      float[] v = new float[3];
      u[0] = p1[0]-p0[0]; u[1] = p1[1]-p0[1]; u[2] = p1[2]-p0[2];
      v[0] = p2[0]-p0[0]; v[1] = p2[1]-p0[1]; v[2] = p2[2]-p0[2];
      normal[0] = u[1]*v[2]-u[2]*v[1];
      normal[1] = u[2]*v[0]-u[0]*v[2];
      normal[2] = u[0]*v[1]-u[1]*v[0];
   }

   @Override
   public void drawAxes(
      RigidTransform3d X, double len, int width, boolean selected) {
      drawAxes (X, new double[] {len, len, len}, width, selected);
   }
   
   public abstract GLTextRenderer getTextRenderer();
   
   public void setDefaultFont(Font font) {
      getTextRenderer().setFont (font);
   }
   
   public Font getDefaultFont() {
      return getTextRenderer().getFont();
   }
   
   public Rectangle2D getTextBounds(Font font, String str, double emSize) {
      return getTextRenderer().getTextBounds (font, str, (float)emSize);
   }
   
   @Override
   public double drawText (String str, float[] loc, double emSize) {
      return drawText (getDefaultFont(), str, loc, emSize);
   }
   
   @Override
   public double drawText (Font font, String str, Point3d loc, double emSize) {
      return drawText (font, str, toFloat(loc), emSize);
   }

   @Override
   public double drawText (String str, Point3d loc, double emSize) {
      return drawText(getDefaultFont (), str, toFloat (loc), emSize);
   }
   
   /**
    * MUST BE CALLED by whatever frame when it is going down, will notify any 
    * shared resources that they can be cleared.  It is best to add it as a
    * WindowListener
    */
   public void dispose() {
      canvas.destroy ();
   }

   //======================================================
   // COLORS and MATERIALS
   //======================================================

   /**
    * {@inheritDoc}
    */
   public boolean setSelectionHighlighting (boolean enable) {
      boolean prev = myHighlightColorActive;
      if (myHighlightStyle == HighlightStyle.COLOR) {
         if (enable != myHighlightColorActive) {
            myHighlightColorActive = enable;
            // indicate that we may need to update color state
            myCurrentMaterialModified = true; 
         }
      }
      else if (myHighlightStyle == HighlightStyle.NONE) {
         // don't do anything ...
      }
      else {
         throw new UnsupportedOperationException (
            "Unsupported highlighting: " + myHighlightStyle);
      }
      return prev;
   }
   
   @Override
   public boolean getSelectionHighlighting() {
      // for now, only color highlighting is implemented
      return myHighlightColorActive;
   }

   @Override
   public void setFrontColor (float[] rgba) {
      myCurrentMaterial.setDiffuse (rgba);
      myCurrentMaterialModified = true;
   }

   @Override
   public float[] getFrontColor (float[] rgba) {
      if (rgba == null) {
         rgba = new float[4];
      }
      myCurrentMaterial.getDiffuse (rgba);
      return rgba;
   }

   @Override
   public void setBackColor (float[] rgba) {
      if (rgba == null) {
         if (myBackColor != null) {
            myBackColor = null;
            myCurrentMaterialModified = true;
         }
      } else {
         if (myBackColor == null) {
            myBackColor = new float[4];
         }
         myBackColor[0] = rgba[0];
         myBackColor[1] = rgba[1];
         myBackColor[2] = rgba[2];
         myBackColor[3] = (rgba.length > 3 ? rgba[3] : 1.0f);
         myCurrentMaterialModified = true;
      }
   }

   public float[] getBackColor (float[] rgba) {
      if (myBackColor == null) {
         return null;
      }
      if (rgba == null) {
         rgba = new float[4];
      }
      rgba[0] = myBackColor[0];
      rgba[1] = myBackColor[1];
      rgba[2] = myBackColor[2];
      if (rgba.length > 3) {
         rgba[3] = myBackColor[3];         
      }
      return rgba;
   }
   
   @Override
   public void setEmission(float[] rgb) {
      myCurrentMaterial.setEmission (rgb);
      myCurrentMaterialModified = true;
   }

   @Override
   public float[] getEmission(float[] rgb) {
      if (rgb == null) {
         rgb = new float[3];
      }
      myCurrentMaterial.getEmission (rgb);
      return rgb;
   }

   @Override
   public void setSpecular(float[] rgb) {
      myCurrentMaterial.setSpecular (rgb);
      myCurrentMaterialModified = true;
   }

   @Override
   public float[] getSpecular(float[] rgb) {
      if (rgb == null) {
         rgb = new float[3];
      }
      myCurrentMaterial.getSpecular (rgb);
      return rgb;
   }

   @Override
   public void setShininess(float s) {
      myCurrentMaterial.setShininess(s);
      myCurrentMaterialModified = true;
   }

   @Override
   public float getShininess () {
      return myCurrentMaterial.getShininess();
   }

   @Override
   public void setFrontAlpha (float a) {
      myCurrentMaterial.setAlpha(a);
      myCurrentMaterialModified = true;
   }

   @Override
   public void setColor (float[] rgba, boolean selected) {
      setFrontColor (rgba);
      setBackColor (null);
      setSelectionHighlighting (selected);      
   }
   
//   public void setColorSelected() {
//      // do we need to set front color?
//      setFrontColor (mySelectedColor);
//      setBackColor (null);
//      setSelectionHighlighting (true);         
//   }

   @Override
   public void setColor (float[] rgba) {
      setFrontColor (rgba);
      setBackColor (null);
   }

   @Override
   public void setColor (float r, float g, float b) {
      setColor(new float[]{r,g,b,1.0f});
   }

   @Override
   public void setColor (float r, float g, float b, float a) {
      setColor(new float[]{r,g,b,a});
   }

   public void setColor (Color color) {
      setColor (color.getColorComponents(null));
   }
   
//   public void setColor (Color color) {
//      setColor(color.getColorComponents (new float[4]));
//   }

//   public void setColor (float[] frontRgba, float[] backRgba) {
//      setColor(frontRgba, backRgba, false);
//   }

//   public void setColor (Color frontColor, Color backColor) {
//      float[] frontRgba = frontColor.getColorComponents(new float[4]);
//      float[] backRgba = backColor.getColorComponents(new float[4]);
//      setColor(frontRgba, backRgba);
//   }

//   @Override
//   public void setColor (float[] frontRgba, float[] backRgba, boolean selected) {
//      setFrontColor (frontRgba);
//      setBackColor (backRgba);
//      setMaterialSelected (selected);
//   }

   public void setMaterial (
      float[] frontRgba, float[] backRgba, float shininess, boolean selected) {
      setFrontColor (frontRgba);
      setBackColor (backRgba);
      setShininess (shininess);
      setSelectionHighlighting (selected);
      setEmission (DEFAULT_MATERIAL_EMISSION);
      setSpecular (DEFAULT_MATERIAL_SPECULAR);
   }

//   @Override
//   public void setMaterial (float[] rgba) {
//      setMaterial(rgba, rgba, DEFAULT_MATERIAL_SHININESS, false);
//   }

   public void setPropsColoring (
      RenderProps props, float[] rgba, boolean selected) {

      setSelectionHighlighting (selected);         
      setFrontColor (rgba);
      if (rgba.length == 3) {
         setFrontAlpha ((float)props.getAlpha());
      }
      setBackColor (null);
      setShininess (props.getShininess());
      setEmission (DEFAULT_MATERIAL_EMISSION);
      float[] specular = props.getSpecularF();
      setSpecular (specular != null ? specular : DEFAULT_MATERIAL_SPECULAR);
   }
   
   public void setLineColoring (RenderProps props, boolean selected) {
      setPropsColoring (props, props.getLineColorF(), selected);
   }

   public void setPointColoring (RenderProps props, boolean selected) {
      setPropsColoring (props, props.getPointColorF(), selected);
   }

   public void setEdgeColoring (RenderProps props, boolean selected) {
      float[] rgba = props.getEdgeColorF();
      if (rgba == null) {
         rgba = props.getLineColorF();
      }
      setPropsColoring (props, rgba, selected);
      setShading (props.getShading());
   }

   public void setFaceColoring (RenderProps props, boolean selected) {
      setFaceColoring (props, props.getFaceColorF(), selected);
   }

   public void setFaceColoring (
      RenderProps props, float[] rgba, boolean selected) {

      setFrontColor (rgba);
      if (rgba.length == 3) {
         setFrontAlpha ((float)props.getAlpha());
      }
      setBackColor (props.getBackColorF());
      setShininess (props.getShininess());
      setEmission (DEFAULT_MATERIAL_EMISSION);
      float[] specular = props.getSpecularF();
      setSpecular (specular != null ? specular : DEFAULT_MATERIAL_SPECULAR);
      setSelectionHighlighting (selected);         
   }

   /**
    * {@inheritDoc}
    */
   public Shading setPointShading (RenderProps props) {
      Shading prevShading = getShading();
      Shading shading;
      if (props.getPointStyle() == PointStyle.POINT) {
         shading = Shading.NONE;
      }
      else {
         shading = props.getShading();
      }
      setShading (shading);
      return prevShading;
   }
   
   /**
    * {@inheritDoc}
    */
   public Shading setLineShading (RenderProps props) {
      Shading prevShading = getShading();
      Shading shading;
      if (props.getLineStyle() == LineStyle.LINE) {
         shading = Shading.NONE;
      }
      else {
         shading = props.getShading();
      }
      setShading (shading);      
      return prevShading;
   }
   
   /**
    * {@inheritDoc}
    */
   public Shading setPropsShading (RenderProps props) {
      Shading prevShading = getShading();
      setShading (props.getShading());      
      return prevShading;
   }

   //=======================================================
   // RENDEROBJECT
   //=======================================================
   
   @Override
   public void draw (RenderObject robj) {
      drawPoints (robj);
      drawLines (robj);
      drawTriangles (robj);
   }
   

   //=======================================================
   // IMMEDIATE DRAW
   //=======================================================

   // data for "drawMode"
   protected DrawMode myDrawMode = null;
   protected boolean myDrawHasNormalData = false;
   protected boolean myDrawHasColorData = false;
   protected boolean myDrawHasTexcoordData = false;
   protected int myDrawVertexIdx = 0;
   protected float[] myDrawCurrentNormal = new float[3];
   protected float[] myDrawCurrentColor = new float[4];
   protected float[] myDrawCurrentTexcoord = new float[2];
   protected int myDrawDataCap = 0;
   protected float[] myDrawVertexData = null;
   protected float[] myDrawNormalData = null;
   protected float[] myDrawColorData = null;
   protected float[] myDrawTexcoordData = null;
   
   // Doing 2D rendering
   protected boolean rendering2d = false;

   protected void ensureDrawDataCapacity () {
      if (myDrawVertexIdx >= myDrawDataCap) {
         int cap = myDrawDataCap;
         if (cap == 0) {
            cap = 1000;
            myDrawVertexData = new float[3*cap];
            if (myDrawHasNormalData) {
               myDrawNormalData = new float[3*cap];
            }
            if (myDrawHasColorData) {
               myDrawColorData = new float[4*cap];
            }
            if (myDrawHasTexcoordData) {
               myDrawTexcoordData = new float[2*cap];
            }
         }
         else {
            cap = (int)(cap*1.5); // make sure cap is a multiple of 3
            myDrawVertexData = Arrays.copyOf (myDrawVertexData, 3*cap);
            if (myDrawHasNormalData) {
               myDrawNormalData = Arrays.copyOf (myDrawNormalData, 3*cap);
            }
            if (myDrawHasColorData) {
               myDrawColorData = Arrays.copyOf (myDrawColorData, 4*cap);
            }
            if (myDrawHasTexcoordData) {
               myDrawTexcoordData = Arrays.copyOf(myDrawTexcoordData, 2*cap);
            }
         }
         myDrawDataCap = cap;
      }
   }      

   /**
    * Returns either Selected (if selected color is currently active)
    * or the current material's diffuse color otherwise
    * 
    * @return current color being used by this viewer
    */
   private float[] getCurrentColor() {
      if (myHighlightColorActive) {
         return myHighlightColor;
      } else {
         return myCurrentMaterial.getDiffuse();
      }
   }

   @Override
   public void beginDraw (DrawMode mode) {
      if (myDrawMode != null) {
         throw new IllegalStateException (
         "Currently in draw mode (i.e., beginDraw() has already been called)");
      }
      myDrawMode = mode;
      myDrawVertexIdx = 0;
      myDrawHasNormalData = false;
      myDrawHasColorData = false;
      myDrawHasTexcoordData = false;

      myDrawCurrentColor = Arrays.copyOf (getCurrentColor(), 4);
      // update materials, because we are going to use 
      // myCurrentMaterialModified to trigger vertex-based coloring
      maybeUpdateMaterials();
   }

   @Override
   public void addVertex (float px, float py, float pz) {
      if (myDrawMode == null) {
         throw new IllegalStateException (
            "Not in draw mode (i.e., beginDraw() has not been called)");
      }
      ensureDrawDataCapacity();

      // check if we need colors
      if (!myDrawHasColorData && myCurrentMaterialModified) {
         // we need to store colors
         myDrawHasColorData = true;
         myDrawColorData = new float[4*myDrawDataCap];
         int cidx = 0;
         // back-fill colors
         for (int i=0; i<myDrawVertexIdx; ++i) {
            myDrawColorData[cidx++] = myDrawCurrentColor[0];
            myDrawColorData[cidx++] = myDrawCurrentColor[1];
            myDrawColorData[cidx++] = myDrawCurrentColor[2];
            myDrawColorData[cidx++] = myDrawCurrentColor[3];
         }
      }

      int vbase = 3*myDrawVertexIdx;
      if (myDrawHasNormalData) {
         myDrawNormalData[vbase  ] = myDrawCurrentNormal[0];
         myDrawNormalData[vbase+1] = myDrawCurrentNormal[1];
         myDrawNormalData[vbase+2] = myDrawCurrentNormal[2];
      }
      if (myDrawHasColorData) {
         int cbase = 4*myDrawVertexIdx;
         myDrawCurrentColor = getCurrentColor ();
         myDrawColorData[cbase  ] = myDrawCurrentColor[0];
         myDrawColorData[cbase+1] = myDrawCurrentColor[1];
         myDrawColorData[cbase+2] = myDrawCurrentColor[2];
         myDrawColorData[cbase+3] = myDrawCurrentColor[3];
      }
      if (myDrawHasTexcoordData) {
         int tbase = 2*myDrawVertexIdx;
         myDrawTexcoordData[tbase ] = myDrawCurrentTexcoord[0];
         myDrawTexcoordData[tbase+1] = myDrawCurrentTexcoord[1];
      }
      myDrawVertexData[vbase] = px;
      myDrawVertexData[++vbase] = py;
      myDrawVertexData[++vbase] = pz;
      ++myDrawVertexIdx;
   }

   @Override
   public void setNormal (float nx, float ny, float nz) {
      if (myDrawMode == null) {
         throw new IllegalStateException (
            "Not in draw mode (i.e., beginDraw() has not been called)");
      }
      myDrawCurrentNormal[0] = nx;
      myDrawCurrentNormal[1] = ny;
      myDrawCurrentNormal[2] = nz;
      if (!myDrawHasNormalData) {
         // back-fill previous normal data
         for (int i=0; i<myDrawVertexIdx; i++) {
            myDrawNormalData[i] = 0f;
         }
         myDrawHasNormalData = true;
      }
   }
   
   @Override
   public void setTextureCoord(float x, float y) {
      if (myDrawMode == null) {
         throw new IllegalStateException (
            "Not in draw mode (i.e., beginDraw() has not been called)");
      }
      myDrawCurrentTexcoord[0] = x;
      myDrawCurrentTexcoord[1] = y;
      if (!myDrawHasTexcoordData) {
         // back-fill previous normal data
         for (int i=0; i<myDrawVertexIdx; i++) {
            myDrawTexcoordData[i] = 0f;
         }
         myDrawHasTexcoordData = true;
      }
   }
   
   @Override
   public void addVertex (double px, double py, double pz) {
      addVertex ((float)px, (float)py, (float)pz);
   }

   @Override
   public void addVertex (Vector3d pnt) {
      addVertex ((float)pnt.x, (float)pnt.y, (float)pnt.z);
   }

   @Override
   public void setNormal (double nx, double ny, double nz) {
      setNormal ((float)nx, (float)ny, (float)nz);
   }

   @Override
   public void setNormal (Vector3d nrm) {
      setNormal ((float)nrm.x, (float)nrm.y, (float)nrm.z);
   }

   @Override
   public void setTextureCoord (double tx, double ty) {
      setTextureCoord((float)tx, (float)ty);
   }
   
   @Override
   public void setTextureCoord (Vector2d tex) {
      setTextureCoord ((float)tex.x, (float)tex.y);
   }
   
   protected int getDrawPrimitive (DrawMode mode) {
      switch (mode) {
         case POINTS:
            return GL.GL_POINTS;
         case LINES:
            return GL.GL_LINES;
         case LINE_LOOP:
            return GL.GL_LINE_LOOP;
         case LINE_STRIP:
            return GL.GL_LINE_STRIP;
         case TRIANGLES:
            return GL.GL_TRIANGLES;
         case TRIANGLE_FAN:
            return GL.GL_TRIANGLE_FAN;
         case TRIANGLE_STRIP:
            return GL.GL_TRIANGLE_STRIP;
         default:
            throw new IllegalArgumentException (
               "Unknown VertexDrawMode: " + mode);
      }
   }

   protected abstract void doDraw(DrawMode drawMode,
      int numVertices, float[] vertexData, 
      boolean hasNormalData, float[] normalData, 
      boolean hasColorData, float[] colorData,
      boolean hasTexcoordData, float[] texcoordData);

   @Override
   public void endDraw() {
      if (myDrawMode == null) {
         throw new IllegalStateException (
            "Not in draw mode (i.e., beginDraw() has not been called)");
      }
      doDraw(myDrawMode, myDrawVertexIdx, myDrawVertexData, 
         myDrawHasNormalData, myDrawNormalData, 
         myDrawHasColorData, myDrawColorData,
         myDrawHasTexcoordData, myDrawTexcoordData);

      myDrawMode = null;

      myDrawDataCap = 0;
      myDrawVertexData = null;
      myDrawNormalData = null;
      myDrawColorData = null;
      myDrawTexcoordData = null;
      
      myDrawVertexIdx = 0;
      myDrawHasNormalData = false;
      myDrawHasColorData = false;
      myDrawHasTexcoordData = false;
   }

   public abstract void maybeUpdateMaterials();
   
}

