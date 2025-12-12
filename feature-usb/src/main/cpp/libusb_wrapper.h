#ifndef LIBUSB_WRAPPER_H
#define LIBUSB_WRAPPER_H

// Forward declarations for libusb types
// These match libusb-1.0 API
struct libusb_context;
struct libusb_device;
struct libusb_device_handle;
struct libusb_device_descriptor;

// libusb error codes
#define LIBUSB_SUCCESS 0
#define LIBUSB_ERROR_IO -1
#define LIBUSB_ERROR_INVALID_PARAM -2
#define LIBUSB_ERROR_ACCESS -3
#define LIBUSB_ERROR_NO_DEVICE -4
#define LIBUSB_ERROR_NOT_FOUND -5
#define LIBUSB_ERROR_BUSY -6
#define LIBUSB_ERROR_TIMEOUT -7
#define LIBUSB_ERROR_OVERFLOW -8
#define LIBUSB_ERROR_PIPE -9
#define LIBUSB_ERROR_INTERRUPTED -10
#define LIBUSB_ERROR_NO_MEM -11
#define LIBUSB_ERROR_NOT_SUPPORTED -12

// USB endpoint direction
#define LIBUSB_ENDPOINT_IN 0x80
#define LIBUSB_ENDPOINT_OUT 0x00

// USB request types
#define LIBUSB_REQUEST_TYPE_STANDARD 0x00
#define LIBUSB_REQUEST_TYPE_CLASS 0x20
#define LIBUSB_REQUEST_TYPE_VENDOR 0x40
#define LIBUSB_REQUEST_TYPE_RESERVED 0x60

// USB recipients
#define LIBUSB_RECIPIENT_DEVICE 0x00
#define LIBUSB_RECIPIENT_INTERFACE 0x01
#define LIBUSB_RECIPIENT_ENDPOINT 0x02
#define LIBUSB_RECIPIENT_OTHER 0x03

// libusb function declarations (will be resolved at link time)
extern "C" {
    // Context management
    int libusb_init(libusb_context **ctx);
    void libusb_exit(libusb_context *ctx);
    
    // Device enumeration
    ssize_t libusb_get_device_list(libusb_context *ctx, libusb_device ***list);
    void libusb_free_device_list(libusb_device **list, int unref_devices);
    
    // Device operations
    int libusb_get_device_descriptor(libusb_device *dev, libusb_device_descriptor *desc);
    uint8_t libusb_get_bus_number(libusb_device *dev);
    uint8_t libusb_get_device_address(libusb_device *dev);
    int libusb_open(libusb_device *dev, libusb_device_handle **handle);
    void libusb_close(libusb_device_handle *handle);
    
    // String descriptors
    int libusb_get_string_descriptor_ascii(libusb_device_handle *dev_handle, uint8_t desc_index, unsigned char *data, int length);
    
    // Transfers
    int libusb_bulk_transfer(libusb_device_handle *dev_handle, unsigned char endpoint, unsigned char *data, int length, int *actual_length, unsigned int timeout);
    int libusb_interrupt_transfer(libusb_device_handle *dev_handle, unsigned char endpoint, unsigned char *data, int length, int *actual_length, unsigned int timeout);
    int libusb_control_transfer(libusb_device_handle *dev_handle, uint8_t bmRequestType, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, unsigned char *data, uint16_t wLength, unsigned int timeout);
    
    // Device reference counting
    libusb_device *libusb_ref_device(libusb_device *dev);
    void libusb_unref_device(libusb_device *dev);
}

// libusb_device_descriptor structure
struct libusb_device_descriptor {
    uint8_t bLength;
    uint8_t bDescriptorType;
    uint16_t bcdUSB;
    uint8_t bDeviceClass;
    uint8_t bDeviceSubClass;
    uint8_t bDeviceProtocol;
    uint8_t bMaxPacketSize0;
    uint16_t idVendor;
    uint16_t idProduct;
    uint16_t bcdDevice;
    uint8_t iManufacturer;
    uint8_t iProduct;
    uint8_t iSerialNumber;
    uint8_t bNumConfigurations;
};

#endif // LIBUSB_WRAPPER_H

