package ca.yorku.eecs.mack.demotiltball78040;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.View;
import android.media.MediaPlayer;
import android.graphics.Path;

import android.util.Log;
import android.widget.Chronometer;


import java.util.Locale;

public class RollingBallPanel extends View
{
    final static float DEGREES_TO_RADIANS = 0.0174532925f;

    // the ball diameter will be min(width, height) / this_value
    final static float BALL_DIAMETER_ADJUST_FACTOR = 30;

    final static int DEFAULT_LABEL_TEXT_SIZE = 20; // tweak as necessary
    final static int DEFAULT_STATS_TEXT_SIZE = 10;
    final static int DEFAULT_GAP = 7; // between lines of text
    final static int DEFAULT_OFFSET = 10; // from bottom of display

    final static int MODE_NONE = 0;
    final static int PATH_TYPE_SQUARE = 1;
    final static int PATH_TYPE_CIRCLE = 2;

    final static float PATH_WIDTH_NARROW = 2f; // ... x ball diameter
    final static float PATH_WIDTH_MEDIUM = 4f; // ... x ball diameter
    final static float PATH_WIDTH_WIDE = 8f; // ... x ball diameter

    float radiusOuter, radiusInner;

    Bitmap ball, decodedBallBitmap;
    int ballDiameter;

    float dT; // time since last sensor event (seconds)

    float width, height, pixelDensity;
    int labelTextSize, statsTextSize, gap, offset;

    RectF innerRectangle, outerRectangle, innerShadowRectangle, outerShadowRectangle, ballNow;
//    Lap line
    float lap_line_x,lap_line_y,lap_line_x_1,lap_line_y_1;
//    Touch Lap line flag
    boolean touch_lap_line_flag;
//    Cheating flag
    int cheating_flag;
    boolean cheating_flag_1;

    int mode;
    RectF[] obstacles;

//    Lap time counter
    long start_time, lap_time, start_time_outside, missed_time;

    final static String MYDEBUG = "MYDEBUG";
//  Inside flag
    boolean inside_flag;

    boolean touchFlag;
    Vibrator vib;
    int wallHits;

    float xBall, yBall; // top-left of the ball (for painting)
    float xBallCenter, yBallCenter; // center of the ball

    float pitch, roll;
    float tiltAngle, tiltMagnitude;

    // parameters from Setup dialog
    String orderOfControl;
    float gain, pathWidth;
    int pathType , lapNumbers;

    float velocity; // in pixels/second (velocity = tiltMagnitude * tiltVelocityGain
    float dBall; // the amount to move the ball (in pixels): dBall = dT * velocity
    float xCenter, yCenter; // the center of the screen
    long now, lastT;
    Paint statsPaint, labelPaint, linePaint, fillPaint, backgroundPaint;
    float[] updateY;

    public RollingBallPanel(Context contextArg)
    {
        super(contextArg);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs)
    {
        super(contextArg, attrs);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs, int defStyle)
    {
        super(contextArg, attrs, defStyle);
        initialize(contextArg);
    }

    // things that can be initialized from within this View
    private void initialize(Context c)
    {

        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(0xffccbbbb);
        fillPaint.setStyle(Paint.Style.FILL);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(DEFAULT_LABEL_TEXT_SIZE);
        labelPaint.setAntiAlias(true);

        statsPaint = new Paint();
        statsPaint.setAntiAlias(true);
        statsPaint.setTextSize(DEFAULT_STATS_TEXT_SIZE);

        // NOTE: we'll create the actual bitmap in onWindowFocusChanged
        decodedBallBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ball);

        lastT = System.nanoTime();
        this.setBackgroundColor(Color.LTGRAY);
        touchFlag = false;
        outerRectangle = new RectF();
        innerRectangle = new RectF();
        innerShadowRectangle = new RectF();
        outerShadowRectangle = new RectF();

        lap_line_x=0;
        lap_line_x_1=0;
        lap_line_y=0;
        lap_line_y_1=0;
        touch_lap_line_flag=false;
        cheating_flag_1=false;
        cheating_flag =0;
        inside_flag =  is_it_inside();
        start_time=System.nanoTime();
        start_time_outside=0;
        missed_time=0;
        lap_time=0;
        obstacles= new RectF[6];

        ballNow = new RectF();
        wallHits = 0;

        vib = (Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Called when the window hosting this view gains or looses focus.  Here we initialize things that depend on the
     * view's width and height.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        if (!hasFocus)
            return;

        width = this.getWidth();
        height = this.getHeight();

        // the ball diameter is nominally 1/30th the smaller of the view's width or height
        ballDiameter = width < height ? (int)(width / BALL_DIAMETER_ADJUST_FACTOR)
                : (int)(height / BALL_DIAMETER_ADJUST_FACTOR);

        // now that we know the ball's diameter, get a bitmap for the ball
        ball = Bitmap.createScaledBitmap(decodedBallBitmap, ballDiameter, ballDiameter, true);

        // center of the view
        xCenter = width / 2f;
        yCenter = height / 2f;

        // top-left corner of the ball
//        xBall = xCenter;
//        yBall = yCenter;



        // center of the ball
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        // configure outer rectangle of the path
        radiusOuter = width < height ? 0.40f * width : 0.40f * height;
        outerRectangle.left = xCenter - radiusOuter;
        outerRectangle.top = yCenter - radiusOuter;
        outerRectangle.right = xCenter + radiusOuter;
        outerRectangle.bottom = yCenter + radiusOuter;

        // configure inner rectangle of the path
        // NOTE: medium path width is 4 x ball diameter
        radiusInner = radiusOuter - pathWidth * ballDiameter;
        innerRectangle.left = xCenter - radiusInner;
        innerRectangle.top = yCenter - radiusInner;
        innerRectangle.right = xCenter + radiusInner;
        innerRectangle.bottom = yCenter + radiusInner;


        lap_line_x = innerRectangle.left;
        lap_line_y = innerRectangle.top+(innerRectangle.bottom-innerRectangle.top)/2;
        lap_line_x_1 = outerRectangle.left;
        lap_line_y_1 = lap_line_y;

        xBall =outerRectangle.left+ (innerRectangle.left-outerRectangle.left)/2;
        yBall = innerRectangle.top+(innerRectangle.bottom-innerRectangle.top)/2;

        // configure outer shadow rectangle (needed to determine wall hits)
        // NOTE: line thickness (aka stroke width) is 2
        outerShadowRectangle.left = outerRectangle.left + ballDiameter - 2f;
        outerShadowRectangle.top = outerRectangle.top + ballDiameter - 2f;
        outerShadowRectangle.right = outerRectangle.right - ballDiameter + 2f;
        outerShadowRectangle.bottom = outerRectangle.bottom - ballDiameter + 2f;

        // configure inner shadow rectangle (needed to determine wall hits)
        innerShadowRectangle.left = innerRectangle.left + ballDiameter - 2f;
        innerShadowRectangle.top = innerRectangle.top + ballDiameter - 2f;
        innerShadowRectangle.right = innerRectangle.right - ballDiameter + 2f;
        innerShadowRectangle.bottom = innerRectangle.bottom - ballDiameter + 2f;

        // initialize a few things (e.g., paint and text size) that depend on the device's pixel density
        pixelDensity = this.getResources().getDisplayMetrics().density;
        labelTextSize = (int)(DEFAULT_LABEL_TEXT_SIZE * pixelDensity + 0.5f);
        labelPaint.setTextSize(labelTextSize);

        statsTextSize = (int)(DEFAULT_STATS_TEXT_SIZE * pixelDensity + 0.5f);
        statsPaint.setTextSize(statsTextSize);

        gap = (int)(DEFAULT_GAP * pixelDensity + 0.5f);
        offset = (int)(DEFAULT_OFFSET * pixelDensity + 0.5f);

        // compute y offsets for painting stats (bottom-left of display)
        updateY = new float[6]; // up to 6 lines of stats will appear
        for (int i = 0; i < updateY.length; ++i)
            updateY[i] = height - offset - i * (statsTextSize + gap);
    }

    /*
     * Do the heavy lifting here! Update the ball position based on the tilt angle, tilt
     * magnitude, order of control, etc.
     */
    public boolean updateBallPosition(float pitchArg, float rollArg, float tiltAngleArg, float tiltMagnitudeArg)
    {
        inside_flag = is_it_inside();
        boolean inside_touching_line= ballTouchingLine();
        pitch = pitchArg; // for information only (see onDraw)
        roll = rollArg; // for information only (see onDraw)
        tiltAngle = tiltAngleArg;
        tiltMagnitude = tiltMagnitudeArg;

        // get current time and delta since last onDraw
        now = System.nanoTime();
        dT = (now - lastT) / 1000000000f; // seconds
        lastT = now;

        // don't allow tiltMagnitude to exceed 45 degrees
        final float MAX_MAGNITUDE = 45f;
        tiltMagnitude = tiltMagnitude > MAX_MAGNITUDE ? MAX_MAGNITUDE : tiltMagnitude;

        // This is the only code that distinguishes velocity-control from position-control
        if (orderOfControl.equals("Velocity")) // velocity control
        {
            // compute ball velocity (depends on the tilt of the device and the gain setting)
            velocity = tiltMagnitude * gain;

            // compute how far the ball should move (depends on the velocity and the elapsed time since last update)
            dBall = dT * velocity; // make the ball move this amount (pixels)

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;



            xBall += dx;
            yBall += dy;

        } else
        // position control
        {
            // compute how far the ball should move (depends on the tilt of the device and the gain setting)
            dBall = tiltMagnitude * gain;

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            xBall = xCenter + dx;
            yBall = yCenter + dy;
        }

        // make an adjustment, if necessary, to keep the ball visible (also, restore if NaN)
        if (Float.isNaN(xBall) || xBall < 0)
            xBall = 0;
        else if (xBall > width - ballDiameter)
            xBall = width - ballDiameter;
        if (Float.isNaN(yBall) || yBall < 0)
            yBall = 0;
        else if (yBall > height - ballDiameter)
            yBall = height - ballDiameter;

        // oh yea, don't forget to update the coordinate of the center of the ball (needed to determine wall  hits)
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        checkCheating();



//        if( cheating_flag==2)
//            Log.i(MYDEBUG,"CHEATING CHECK PASSS");

//        If it touched the Lap number Line
        if(touchedLapLine() && cheating_flag%2==0 && cheating_flag>0)
        {
            lap_time += System.nanoTime() - start_time;
            start_time = System.nanoTime();
            if(cheating_flag==2*lapNumbers)
            {
                Log.i(MYDEBUG,"sound");
                MediaPlayer mp = MediaPlayer.create(this.getContext(), R.raw.my_tone);
                mp.start();
                cheating_flag=0;
                Bundle b = new Bundle();
                b.putInt("wall_hints", wallHits);
                b.putInt("number_laps", lapNumbers);

                long temporal = ((lap_time - missed_time)*100)/lap_time;
                Double tempa_1 =Math.round(temporal * 100.0) / 100.0;
                b.putDouble("in_path_time", tempa_1);

                Double tempa = Math.round( ( (lap_time / 1000000000f)/lapNumbers) * 100.0) / 100.0;
                b.putDouble("lap_time", tempa);

                Intent i = new Intent(this.getContext(), ResultsActivity.class);
                i.putExtras(b);
                this.getContext().startActivity(i);
                return true;
            }
        }

        if(mode==0 && did_fall())
        {
            Log.i(MYDEBUG,"FELL");
            lap_time += System.nanoTime() - start_time;
            start_time = System.nanoTime();

            MediaPlayer mp = MediaPlayer.create(this.getContext(), R.raw.game_over);
            mp.start();
            cheating_flag=0;
            Bundle b = new Bundle();
            b.putInt("wall_hints", wallHits);
            b.putInt("number_laps", lapNumbers);
            long temporal = ((lap_time - missed_time)*100)/lap_time;
            Double tempa_1 =Math.round(temporal * 100.0) / 100.0;
            b.putDouble("in_path_time", tempa_1);
            Double tempa = Math.round( ( (lap_time / 1000000000f)/lapNumbers) * 100.0) / 100.0;
            b.putDouble("lap_time", tempa);
            Intent i = new Intent(this.getContext(), ResultsActivity.class);
            i.putExtras(b);
            this.getContext().startActivity(i);
            return true;

        }


        // if ball touches wall, vibrate and increment wallHits count
        // NOTE: We also use a boolean touchFlag so we only vibrate on the first touch
        if (ballTouchingLine() && !touchFlag )
        {
            touchFlag = true; // the ball has *just* touched the line: set the touchFlag
            if(inside_flag) {
                vib.vibrate(50); // 50 ms vibrotactile pulse
                ++wallHits;
                start_time_outside = System.nanoTime();
            }else
                missed_time += System.nanoTime()-start_time_outside;

        } else if (!ballTouchingLine() && touchFlag)
            touchFlag = false; // the ball is no longer touching the line: clear the touchFlag

        invalidate(); // force onDraw to redraw the screen with the ball in its new position
        return false;
    }

    private void drawArrow(Canvas canvas, float x0, float y0, float x1, float y1) {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);

        float deltaX = x1 - x0;
        float deltaY = y1 - y0;
        double distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
        float frac = (float) (1 / (distance / 30));

        float point_x_1 = x0 + (float) ((1 - frac) * deltaX + frac * deltaY);
        float point_y_1 = y0 + (float) ((1 - frac) * deltaY - frac * deltaX);

        float point_x_2 = x1;
        float point_y_2 = y1;

        float point_x_3 = x0 + (float) ((1 - frac) * deltaX - frac * deltaY);
        float point_y_3 = y0 + (float) ((1 - frac) * deltaY + frac * deltaX);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);

        path.moveTo(point_x_1, point_y_1);
        path.lineTo(point_x_2, point_y_2);
        path.lineTo(point_x_3, point_y_3);
        path.lineTo(point_x_1, point_y_1);
        path.lineTo(point_x_1, point_y_1);
        path.close();

        canvas.drawPath(path, paint);
        paint.setStrokeWidth(7);
        canvas.drawLine(x0,y0,x1,y1, paint);
    }

    protected void onDraw(Canvas canvas)
    {

        // draw the paths
        if (pathType == PATH_TYPE_SQUARE)
        {
            // draw fills
            canvas.drawRect(outerRectangle, fillPaint);
            canvas.drawRect(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawRect(outerRectangle, linePaint);
            canvas.drawRect(innerRectangle, linePaint);


            linePaint.setStrokeWidth(7);
            linePaint.setColor(Color.BLUE);
            canvas.drawLine(lap_line_x,lap_line_y,lap_line_x_1,lap_line_y_1, linePaint);
            linePaint.setStrokeWidth(3);
            linePaint.setColor(Color.RED);

            drawArrow(canvas,lap_line_x_1-100,lap_line_y_1,lap_line_x_1-100,lap_line_y_1+150);

        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            // draw fills
            canvas.drawOval(outerRectangle, fillPaint);
            canvas.drawOval(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawOval(outerRectangle, linePaint);
            canvas.drawOval(innerRectangle, linePaint);

            linePaint.setStrokeWidth(7);
            linePaint.setColor(Color.BLUE);
            canvas.drawLine(lap_line_x,lap_line_y,lap_line_x_1,lap_line_y_1, linePaint);
            linePaint.setColor(Color.RED);
            linePaint.setStrokeWidth(3);

            drawArrow(canvas,lap_line_x_1-100,lap_line_y_1,lap_line_x_1-100,lap_line_y_1+150);

        }


        if(mode==0) {
            initialize_obstacles();
            linePaint.setColor(Color.BLACK);
            backgroundPaint.setColor(Color.BLACK);
            for (int i = 0; i < 6; i++) {
                canvas.drawOval(obstacles[i], backgroundPaint);
                canvas.drawOval(obstacles[i], linePaint);
            }
            linePaint.setColor(Color.RED);
            backgroundPaint.setColor(Color.LTGRAY);
        }

        // draw label
        canvas.drawText("Demo_TiltBall", 6f, labelTextSize, labelPaint);

        // draw stats (pitch, roll, tilt angle, tilt magnitude)
        if (pathType == PATH_TYPE_SQUARE || pathType == PATH_TYPE_CIRCLE)
        {
            canvas.drawText("Wall hits = " + wallHits, 6f, updateY[5], statsPaint);
            canvas.drawText("-----------------", 6f, updateY[4], statsPaint);
        }
        canvas.drawText(String.format(Locale.CANADA, "Tablet pitch (degrees) = %.2f", pitch), 6f, updateY[3],
                statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Tablet roll (degrees) = %.2f", roll), 6f, updateY[2], statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Ball x = %.2f", xBallCenter), 6f, updateY[1], statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Ball y = %.2f", yBallCenter), 6f, updateY[0], statsPaint);

        // draw the ball in its new location
        canvas.drawBitmap(ball, xBall, yBall, null);

    } // end onDraw

    /*
     * Configure the rolling ball panel according to setup parameters
     */
    public void configure(String pathMode, String pathWidthArg, int gainArg, String orderOfControlArg, int lapNumbersArg, int modeArg)
    {
        // square vs. circle
        if (pathMode.equals("Square"))
            pathType = PATH_TYPE_SQUARE;
        else if (pathMode.equals("Circle"))
            pathType = PATH_TYPE_CIRCLE;
        else
            pathType = MODE_NONE;

        // narrow vs. medium vs. wide
        if (pathWidthArg.equals("Narrow"))
            pathWidth = PATH_WIDTH_NARROW;
        else if (pathWidthArg.equals("Wide"))
            pathWidth = PATH_WIDTH_WIDE;
        else
            pathWidth = PATH_WIDTH_MEDIUM;

        gain = gainArg;

        lapNumbers=lapNumbersArg;
        mode=modeArg;
        Log.i(MYDEBUG, Integer.toString(mode)+" BBBBBBBBBB");

        orderOfControl = orderOfControlArg;
    }

    // returns true if the ball is touching (i.e., overlapping) the line of the inner or outer path border
    public boolean ballTouchingLine()
    {

        if (pathType == PATH_TYPE_SQUARE)
        {
            ballNow.left = xBall;
            ballNow.top = yBall;
            ballNow.right = xBall + ballDiameter;
            ballNow.bottom = yBall + ballDiameter;

            if (RectF.intersects(ballNow, outerRectangle) && !RectF.intersects(ballNow, outerShadowRectangle))
                return true; // touching outside rectangular border

            if (RectF.intersects(ballNow, innerRectangle) && !RectF.intersects(ballNow, innerShadowRectangle))
                return true; // touching inside rectangular border

        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));

            if (Math.abs(ballDistance - radiusOuter) < (ballDiameter / 2f))
                return true; // touching outer circular border

            if (Math.abs(ballDistance - radiusInner) < (ballDiameter / 2f))
                return true; // touching inner circular border
        }
        return false;
    }

    public boolean touchedLapLine()
    {
        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;

        RectF temp= new RectF();
        temp.bottom = lap_line_y;
        temp.top = lap_line_y;
        temp.right = lap_line_x;
        temp.left = lap_line_x_1;

        RectF temp_shadow = new RectF();

        temp_shadow.left = temp.left + ballDiameter - 2f;
        temp_shadow.top = temp.top + ballDiameter - 2f;
        temp_shadow.right = temp.right - ballDiameter + 2f;
        temp_shadow.bottom = temp.bottom - ballDiameter + 2f;


        if (RectF.intersects(ballNow,temp) && !RectF.intersects(ballNow, temp_shadow))
            return true;
        return false;
    }

    public boolean did_fall()
    {
        initialize_obstacles();

        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;


        for (int i=0; i< 6; i++)
            if(RectF.intersects(ballNow,obstacles[i]))
                return true;
        return false;
    }

    private void initialize_obstacles(){
        //middle bottom
        obstacles[0] = new RectF();
        obstacles[0].left = xCenter;
        obstacles[0].top = innerRectangle.bottom;
        obstacles[0].right = obstacles[0].left + ballDiameter * (3 / 2);
        obstacles[0].bottom = obstacles[0].top + ballDiameter * (3 / 2);
        //middle top
        obstacles[1] = new RectF();
        obstacles[1].left = xCenter - ballDiameter * (3 / 2);
        obstacles[1].top = innerRectangle.top - ballDiameter * (3 / 2);
        obstacles[1].right = xCenter;
        obstacles[1].bottom = innerRectangle.top;
        //middle right
        obstacles[2] = new RectF();
        obstacles[2].left = innerRectangle.right;
        obstacles[2].top = yCenter;
        obstacles[2].right = innerRectangle.right + ballDiameter * (3 / 2);
        obstacles[2].bottom = yCenter + ballDiameter * (3 / 2);
        //bottom corner right
        obstacles[3] = new RectF();
        obstacles[3].left = outerRectangle.right - ballDiameter * (3 / 2);
        obstacles[3].top = outerRectangle.bottom - ballDiameter * (3 / 2);
        obstacles[3].right = outerRectangle.right;
        obstacles[3].bottom = outerRectangle.bottom;
        //top corner right
        obstacles[4] = new RectF();
        obstacles[4].left = outerRectangle.right - ballDiameter * (3 / 2);
        obstacles[4].top = outerRectangle.top;
        obstacles[4].right = outerRectangle.right;
        obstacles[4].bottom = outerRectangle.top + ballDiameter * (3 / 2);
        //top corner left
        obstacles[5] = new RectF();
        obstacles[5].left = outerRectangle.left;
        obstacles[5].top = outerRectangle.top;
        obstacles[5].right = outerRectangle.left + ballDiameter * (3 / 2);
        obstacles[5].bottom = outerRectangle.top + ballDiameter * (3 / 2);
    }
    public void checkCheating()
    {
        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;

        RectF temp= new RectF();
        temp.bottom = 100000;
        temp.top = yCenter;
        temp.right = xCenter;
        temp.left = xCenter;

        RectF temp_shadow = new RectF();

        temp_shadow.left = temp.left + ballDiameter - 2f;
        temp_shadow.top = temp.top + ballDiameter - 2f;
        temp_shadow.right = temp.right - ballDiameter + 2f;
        temp_shadow.bottom = temp.bottom - ballDiameter + 2f;


        if (RectF.intersects(ballNow,temp) && !RectF.intersects(ballNow, temp_shadow) && cheating_flag%2==0) {
            cheating_flag ++;
            Log.i(MYDEBUG,"Bottom "+Integer.toString(cheating_flag));
        }

        temp.bottom = yCenter;
        temp.top = 0;

        temp_shadow.left = temp.left + ballDiameter - 2f;
        temp_shadow.top = temp.top + ballDiameter - 2f;
        temp_shadow.right = temp.right - ballDiameter + 2f;
        temp_shadow.bottom = temp.bottom - ballDiameter + 2f;

        if (RectF.intersects(ballNow,temp) && !RectF.intersects(ballNow, temp_shadow) && cheating_flag%2==1) {
            cheating_flag ++;
            Log.i(MYDEBUG,"top "+Integer.toString(cheating_flag));
        }

    }
    public boolean is_it_inside()
    {
        if (pathType == PATH_TYPE_SQUARE)
        {
            ballNow.left = xBall;
            ballNow.top = yBall;
            ballNow.right = xBall + ballDiameter;
            ballNow.bottom = yBall + ballDiameter;

            if( RectF.intersects(ballNow, innerRectangle))
                return false;
            if( RectF.intersects(ballNow, outerRectangle))
                return true;
            return false;

        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));

            if (ballDistance > radiusInner && ballDistance<radiusOuter)
                return true;
            return false;
        }
        return false;
    }
}
