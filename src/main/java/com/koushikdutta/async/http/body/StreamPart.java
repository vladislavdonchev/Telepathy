package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;

import org.apache.http.NameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public abstract class StreamPart extends Part {
    public StreamPart(String name, long length, List<NameValuePair> contentDisposition) {
        super(name, length, contentDisposition);
    }

    @Override
    public void write(DataSink sink, CompletedCallback callback) {
        try {
            InputStream is = getInputStream();
            com.koushikdutta.async.Util.pump(is, sink, callback);
        } catch (Exception e) {
            callback.onCompleted(e);
        }
    }

    protected abstract InputStream getInputStream() throws IOException;
}
