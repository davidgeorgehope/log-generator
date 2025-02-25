# log-generator

A utility for generating realistic logs for testing and demonstration purposes. The tool can generate logs in different formats.

## Supported Log Formats

### Standard NGINX Logs
The default format follows the standard NGINX access log format.

### NGINX Ingress Controller Logs
Added support for NGINX Ingress Controller log format which includes additional fields like upstream response time, upstream status, and host.

## Usage

Start the generator with:

```bash
./start-log-generator.sh [options]
```

### Options

- `--log-format=standard|ingress` - Set the log format (default: standard)
- `--nginx-ingress-port=PORT` - Port to send NGINX Ingress logs to (default: 9007)
- `--mean-requests-per-second=VALUE` - Average number of requests per second (default: 1)
- `--no-anomalies` - Disable anomaly generation
- Other port options for streaming logs

### Examples

To generate standard NGINX logs:
```bash
./start-log-generator.sh --log-format=standard
```

To generate NGINX Ingress Controller logs:
```bash
./start-log-generator.sh --log-format=ingress
```

To stop the generator:
```bash
./stop-log-generator.sh
```
