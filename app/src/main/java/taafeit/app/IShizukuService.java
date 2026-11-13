/*
 * Auto-generated from app/src/main/aidl/taafeit/app/IShizukuService.aidl.
 *
 * Checked in intentionally: AGP 9.x fails to parse the AIDL dependency file
 * when the project path contains non-ASCII characters. After editing the
 * .aidl file, regenerate this stub from an ASCII-only path:
 *
 *   aidl.exe -p <sdk>/platforms/android-37.0/framework.aidl \
 *            -o <ascii-out> -I <ascii-src> <ascii-src>/taafeit/app/IShizukuService.aidl
 *
 * Do not edit the interface by hand.
 */
package taafeit.app;
public interface IShizukuService extends android.os.IInterface
{
  /** Default implementation for IShizukuService. */
  public static class Default implements taafeit.app.IShizukuService
  {
    @Override public java.lang.String runCommand(java.lang.String cmd) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements taafeit.app.IShizukuService
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an taafeit.app.IShizukuService interface,
     * generating a proxy if needed.
     */
    public static taafeit.app.IShizukuService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof taafeit.app.IShizukuService))) {
        return ((taafeit.app.IShizukuService)iin);
      }
      return new taafeit.app.IShizukuService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_runCommand:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.runCommand(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements taafeit.app.IShizukuService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String runCommand(java.lang.String cmd) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(cmd);
          boolean _status = mRemote.transact(Stub.TRANSACTION_runCommand, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_runCommand = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "taafeit.app.IShizukuService";
  public java.lang.String runCommand(java.lang.String cmd) throws android.os.RemoteException;
  public void destroy() throws android.os.RemoteException;
}
