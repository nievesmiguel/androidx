/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.core;

import android.graphics.ImageFormat;
import android.media.Image;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.impl.ImageReaderProxy;

import java.nio.ByteBuffer;

/** Utility class to convert an {@link Image} from YUV to RGB. */
final class ImageYuvToRgbConverter {

    private static final String TAG = "ImageYuvToRgbConverter";

    static {
        System.loadLibrary("yuv_to_rgb_jni");
    }

    enum Result {
        UNKNOWN,
        SUCCESS,
        ERROR_FORMAT, // YUV format error.
        ERROR_CONVERSION,  // Native conversion error.
    }

    private ImageYuvToRgbConverter() {
    }

    /**
     * Converts image proxy in YUV to RGB.
     *
     * Currently this config supports the devices which generated NV21, NV12, I420 YUV layout,
     * otherwise the input YUV layout will be converted to NV12 first and then to RGBA_8888 as a
     * fallback.
     *
     * @param imageProxy input image proxy in YUV.
     * @param rgbImageReaderProxy output image reader proxy in RGB.
     * @return output image proxy in RGB.
     */
    @Nullable
    public static ImageProxy convertYUVToRGB(
            ImageProxy imageProxy,
            ImageReaderProxy rgbImageReaderProxy) {
        if (!ImageYuvToRgbConverter.isSupportedYUVFormat(imageProxy)) {
            Logger.e(TAG, "Unsupported format for YUV to RGB");
            return null;
        }

        // Convert YUV To RGB and write data to surface
        ImageYuvToRgbConverter.Result result = convertYUVToRGBInternal(
                imageProxy, rgbImageReaderProxy.getSurface());

        if (result == Result.ERROR_CONVERSION) {
            Logger.e(TAG, "YUV to RGB conversion failure");
            return null;
        }

        if (result == Result.ERROR_FORMAT) {
            Logger.e(TAG, "Unsupported format for YUV to RGB");
            return null;
        }

        // Retrieve ImageProxy in RGB
        ImageProxy rgbImageProxy = rgbImageReaderProxy.acquireLatestImage();

        // Close YUV image proxy for the next
        if (rgbImageProxy != null) {
            imageProxy.close();
        }
        return rgbImageProxy;
    }

    /**
     * Checks whether input image is in supported YUV format.
     *
     * @param imageProxy input image proxy in YUV.
     * @return true if in supported YUV format, false otherwise.
     */
    private static boolean isSupportedYUVFormat(@NonNull ImageProxy imageProxy) {
        return imageProxy.getFormat() == ImageFormat.YUV_420_888
                && imageProxy.getPlanes().length == 3;
    }

    /**
     * Converts image from YUV to RGB.
     *
     * @param imageProxy input image proxy in YUV.
     * @param surface output surface for RGB data.
     * @return {@link Result}.
     */
    @NonNull
    private static Result convertYUVToRGBInternal(
            @NonNull ImageProxy imageProxy,
            @NonNull Surface surface) {
        if (!isSupportedYUVFormat(imageProxy)) {
            return Result.ERROR_FORMAT;
        }

        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int startOffset = 0;
        int srcStrideY = imageProxy.getPlanes()[0].getRowStride();
        int srcStrideU = imageProxy.getPlanes()[1].getRowStride();
        int srcStrideV = imageProxy.getPlanes()[2].getRowStride();
        int srcPixelStrideUV = imageProxy.getPlanes()[1].getPixelStride();

        int result = convertAndroid420ToABGR(
                imageProxy.getPlanes()[0].getBuffer(),
                srcStrideY,
                imageProxy.getPlanes()[1].getBuffer(),
                srcStrideU,
                imageProxy.getPlanes()[2].getBuffer(),
                srcStrideV,
                srcPixelStrideUV,
                surface,
                imageWidth,
                imageHeight,
                startOffset);
        if (result != 0) {
            return Result.ERROR_CONVERSION;
        }
        return Result.SUCCESS;
    }

    /**
     * Converts Android YUV_420_888 to RGBA.
     *
     * @param srcByteBufferY Source Y data.
     * @param srcStrideY Source Y row stride.
     * @param srcByteBufferU Source U data.
     * @param srcStrideU Source U row stride.
     * @param srcByteBufferV Source V data.
     * @param srcStrideV Source V row stride.
     * @param srcPixelStrideUV Pixel stride for UV.
     * @param surface Destination surface for ABGR data.
     * @param width Destination image width.
     * @param height Destination image height.
     * @param startOffset Position in source data to begin reading from.
     * @return zero if succeeded, otherwise non-zero.
     */
    private static native int convertAndroid420ToABGR(
            @NonNull ByteBuffer srcByteBufferY,
            int srcStrideY,
            @NonNull ByteBuffer srcByteBufferU,
            int srcStrideU,
            @NonNull ByteBuffer srcByteBufferV,
            int srcStrideV,
            int srcPixelStrideUV,
            @NonNull Surface surface,
            int width,
            int height,
            int startOffset);

}