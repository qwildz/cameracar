#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

#define  LOG_TAG    "Native"
#define  LOG_D(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)


using namespace std;
using namespace cv;

//Scalar _lowerThreshold(60, 100, 30); // Green
//Scalar _upperThreshold(130, 255, 255);

Scalar _lowerThreshold(0, 125, 150); // Green
Scalar _upperThreshold(60, 255, 255);

Mat _hsvMat;
Mat _processedMat;
Mat _dilatedMat;

double _contourArea = 7;
vector<vector<Point> > contours;
vector<Vec4i> hierarchy;
Point2f _centerPoint(-1, -1);

int size = 3;
//jdoubleArray data;
jdouble tmp[3];

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jdoubleArray JNICALL
Java_com_qwildz_carcamera_CameraActivity_detectTennisBall2(JNIEnv *env, jobject instance,
                                                           jlong matAddrGray, jint iLowH,
                                                           jint iLowS, jint iLowV, jint iHighH,
                                                           jint iHighS, jint iHighV,
                                                           jboolean showHsv) {

    Mat &mGr = *(Mat *) matAddrGray;

    cvtColor(mGr, _hsvMat, COLOR_RGB2HSV_FULL);

    inRange(_hsvMat, Scalar(iLowH, iLowS, iLowV), Scalar(iHighH, iHighS, iHighV), _processedMat);

    erode(_processedMat, _dilatedMat, Mat());

    if (!showHsv)
        findContours(_dilatedMat.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
    vector<Point2f> points;
    _contourArea = 7;

    if (!showHsv) {
        for (int i = 0, n = contours.size(); i < n; i++) {
            double current_contour = contourArea(contours[i]);
            if (current_contour > _contourArea) {
                _contourArea = current_contour;
                Mat(contours[i]).convertTo(points, CV_32FC2);
            }
        }
    }

    if (!showHsv) {
        if (!points.empty() && _contourArea > 100) {
            float radius;
            cv::minEnclosingCircle(points, _centerPoint, radius);
            circle(mGr, _centerPoint, cvRound(cvSqrt(_contourArea / CV_PI)), Scalar(255, 0, 0));
        }
    } else {
        _dilatedMat.copyTo(mGr);
    }

    jdoubleArray data = env->NewDoubleArray(size);

    tmp[0] = _contourArea;
    tmp[1] = _centerPoint.x;
    tmp[2] = _centerPoint.y;

    env->SetDoubleArrayRegion(data, 0, size, tmp);

    return data;
}

JNIEXPORT jdouble JNICALL
Java_com_qwildz_carcamera_CameraActivity_detectTennisBall(JNIEnv *env, jobject instance,
                                                          jlong matAddrGray) {
    Mat &mGr = *(Mat *) matAddrGray;

    cvtColor(mGr, _hsvMat, COLOR_RGB2HSV_FULL);

    inRange(_hsvMat, _lowerThreshold, _upperThreshold, _processedMat);

    erode(_processedMat, _dilatedMat, Mat());
    findContours(_dilatedMat, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

    vector<Point2f> points;
    _contourArea = 7;
    for (int i = 0, n = contours.size(); i < n; i++) {
        double current_contour = contourArea(contours[i]);
        if (current_contour > _contourArea) {
            _contourArea = current_contour;
            Mat(contours[i]).convertTo(points, CV_32FC2);
        }
    }

    if (!points.empty() && _contourArea > 100) {
        float radius;
        cv::minEnclosingCircle(points, _centerPoint, radius);
        circle(mGr, _centerPoint, cvRound(cvSqrt(_contourArea / CV_PI)), Scalar(255, 0, 0));
    }

    return _contourArea;
}

jstring
Java_com_qwildz_carcamera_CameraActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

void
Java_com_qwildz_carcamera_CameraActivity_salt(
        JNIEnv *env,
        jobject instance,
        jlong matAddrGray,
        jint nbrElem) {
    Mat &mGr = *(Mat *) matAddrGray;
    for (int k = 0; k < nbrElem; k++) {
        int i = rand() % mGr.cols;
        int j = rand() % mGr.rows;
        mGr.at<uchar>(j, i) = 255;
    }
}

void
Java_com_qwildz_carcamera_CameraActivity_blurImage(JNIEnv *env, jobject instance, jlong matAddr) {
    Mat &mGr = *(Mat *) matAddr;
    GaussianBlur(mGr, mGr, Size(9, 9), 2, 2);
}

void
Java_com_qwildz_carcamera_CameraActivity_detectCircle(JNIEnv *env, jobject instance,
                                                      jlong matAddr) {
    Mat &mGr = *(Mat *) matAddr;
    vector<Vec3f> circles;
    HoughCircles(mGr, circles, CV_HOUGH_GRADIENT, 1, mGr.rows / 8, 100, 20, 0, 0);

    if (circles.size() > 0) {
        for (size_t i = 0; i < circles.size(); i++) {
            Point center(round(circles[i][0]), round(circles[i][1]));
            int radius = round(circles[i][2]);

            circle(mGr, center, radius, Scalar(0, 255, 0), 5);
        }
    }
}


void
Java_com_qwildz_carcamera_CameraActivity_detectRed(JNIEnv *env, jobject instance, jlong matAddr,
                                                   jint iLowH,
                                                   jint iLowS,
                                                   jint iLowV,
                                                   jint iHighH,
                                                   jint iHighS,
                                                   jint iHighV) {

    Mat &mGr = *(Mat *) matAddr;

    Mat original = mGr.clone();
    medianBlur(mGr, mGr, 3);

    Mat hsv;
    cvtColor(mGr, hsv, COLOR_RGB2HSV); //Convert the captured frame from BGR to HSV



    Mat lower;
    Mat upper;

    inRange(hsv, Scalar(0, 100, 100), Scalar(10, 255, 255), lower);
    inRange(hsv, Scalar(160, 100, 100), Scalar(179, 255, 255), upper);

    Mat red_hue;
    addWeighted(lower, 1, upper, 1, 0, red_hue);

    GaussianBlur(red_hue, red_hue, Size(9, 9), 2, 2);

    //

    vector<Vec3f> circles;
    HoughCircles(red_hue, circles, CV_HOUGH_GRADIENT, 1, red_hue.rows / 8, 100, 20, 0, 0);

    if (circles.size() > 0) {
        for (size_t i = 0; i < circles.size(); i++) {
            Point center(round(circles[i][0]), round(circles[i][1]));
            int radius = round(circles[i][2]);

            circle(original, center, radius, Scalar(0, 255, 0), 5);
        }
    }

    original.copyTo(mGr);

    //Java_com_qwildz_carcamera_CameraActivity_blurImage(env, instance, (jlong) &red_hue);
    //Java_com_qwildz_carcamera_CameraActivity_detectCircle(env, instance, matAddr);

    //inRange(mGr, Scalar(iLowH, iLowS, iLowV), Scalar(iHighH, iHighS, iHighV), mGr); //Threshold the image
    //inRange(mGr, Scalar(iLowH, iLowS, iLowV), Scalar(iHighH, iHighS, iHighV), mGr); //Threshold the image


//
//    //morphological opening (remove small objects from the foreground)
//    erode(imgThresholded, imgThresholded, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
//    dilate(imgThresholded, imgThresholded, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
//
//    //morphological closing (fill small holes in the foreground)
//    dilate(imgThresholded, imgThresholded, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
//    erode(imgThresholded, mGr, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
}

#ifdef __cplusplus
}
#endif