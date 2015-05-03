package net.hardcodes.telepathy.tools;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public class CodecUtils {
    public static final int TIMEOUT_USEC = 1000000;
    public static final int VIDEO_META_MAX_LEN = 64;

    //    public static final String MIME_TYPE = "video/x-vnd.on2.vp8";
    public static final String MIME_TYPE = "video/avc";
}