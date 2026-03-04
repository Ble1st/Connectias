//! JNI bridge for Android: Rust calls back to Kotlin for bulk transfers.

use jni::objects::{GlobalRef, JByteArray, JObject, JValue};
use jni::JNIEnv;
use std::io;

/// Transfer handler that calls Kotlin BulkTransferHandler via JNI.
pub struct JniTransferHandler {
    vm: jni::JavaVM,
    handler: GlobalRef,
}

impl JniTransferHandler {
    pub fn new(env: &mut JNIEnv<'_>, handler: JObject<'_>) -> io::Result<Self> {
        let vm = env.get_java_vm().map_err(|e| {
            io::Error::new(io::ErrorKind::Other, format!("get_java_vm failed: {:?}", e))
        })?;
        let handler = env.new_global_ref(handler).map_err(|e| {
            io::Error::new(io::ErrorKind::Other, format!("new_global_ref failed: {:?}", e))
        })?;
        Ok(Self { vm, handler })
    }

    fn with_env<F, R>(&self, f: F) -> io::Result<R>
    where
        F: FnOnce(&mut JNIEnv<'_>) -> jni::errors::Result<R>,
    {
        let mut guard = self.vm.attach_current_thread_permanently().map_err(|e| {
            io::Error::new(io::ErrorKind::Other, format!("attach thread failed: {:?}", e))
        })?;
        f(&mut guard).map_err(|e| {
            io::Error::new(io::ErrorKind::Other, format!("JNI call failed: {:?}", e))
        })
    }

}

impl crate::block_device::TransferHandler for JniTransferHandler {
    fn bulk_out(&self, _session_id: u64, data: &[u8]) -> io::Result<usize> {
        self.with_env(|env| {
            let arr = env.byte_array_from_slice(data)?;
            let result = env.call_method(
                &self.handler,
                "bulkOut",
                "([B)I",
                &[JValue::Object(&arr.into())],
            )?;
            let n: i32 = result.i()?;
            Ok(n as usize)
        })
    }

    fn bulk_in(&self, _session_id: u64, buf: &mut [u8]) -> io::Result<usize> {
        self.with_env(|env| {
            let result = env.call_method(
                &self.handler,
                "bulkIn",
                "(I)[B",
                &[JValue::Int(buf.len() as i32)],
            )?;
            let result_arr = result.l()?;
            let byte_arr = unsafe { JByteArray::from_raw(result_arr.as_raw() as jni::sys::jarray) };
            let len = env.get_array_length(&byte_arr)?;
            let copy_len = (len as usize).min(buf.len());
            if copy_len > 0 {
                let mut temp = vec![0i8; copy_len];
                env.get_byte_array_region(&byte_arr, 0, &mut temp)?;
                for i in 0..copy_len {
                    buf[i] = temp[i] as u8;
                }
            }
            Ok(copy_len)
        })
    }
}

unsafe impl Send for JniTransferHandler {}
