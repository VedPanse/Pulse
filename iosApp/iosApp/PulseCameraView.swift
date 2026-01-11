import SwiftUI
import AVFoundation

struct PulseCameraView: View {
    @StateObject private var viewModel = PulseViewModel()
    @State private var cameraAuthorized = false

    var body: some View {
        ZStack {
            if cameraAuthorized {
                CameraPreview(position: .back)
            } else {
                Color.black
                Text("Camera access required")
                    .foregroundColor(.white)
            }
            ClusterDots(clusters: viewModel.clusters)
            VStack {
                Spacer()
                ClusterOverlay(clusters: viewModel.clusters)
            }
        }
        .onAppear {
            requestCameraAccess()
        }
    }

    private func requestCameraAccess() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraAuthorized = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    cameraAuthorized = granted
                }
            }
        default:
            cameraAuthorized = false
        }
    }
}

struct ClusterOverlay: View {
    let clusters: [ClusterViewData]

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                ForEach(clusters) { cluster in
                    ClusterCardView(cluster: cluster)
                }
            }
            .padding(12)
        }
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.5))
    }
}

struct ClusterDots: View {
    let clusters: [ClusterViewData]

    var body: some View {
        GeometryReader { proxy in
            ForEach(clusters) { cluster in
                PulsingDot()
                    .position(dotPosition(for: cluster.id, in: proxy.size))
            }
        }
        .allowsHitTesting(false)
    }

    private func dotPosition(for clusterId: String, in size: CGSize) -> CGPoint {
        let hash = clusterId.unicodeScalars.reduce(0) { $0 * 31 + Int($1.value) }
        let xSeed = CGFloat((hash & 0xFFFF) % 1000) / 1000.0
        let ySeed = CGFloat(((hash >> 16) & 0xFFFF) % 1000) / 1000.0
        let padding: CGFloat = 40
        let x = padding + (size.width - padding * 2) * xSeed
        let y = padding + (size.height - padding * 2) * ySeed
        return CGPoint(x: x, y: y)
    }
}

struct PulsingDot: View {
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(Color.red.opacity(0.8))
            .frame(width: pulse ? 20 : 10, height: pulse ? 20 : 10)
            .overlay(
                Circle()
                    .stroke(Color.red.opacity(0.6), lineWidth: 2)
                    .frame(width: pulse ? 36 : 20, height: pulse ? 36 : 20)
                    .opacity(pulse ? 0.2 : 0.8)
            )
            .onAppear {
                withAnimation(
                    Animation.easeInOut(duration: 1.2)
                        .repeatForever(autoreverses: true)
                ) {
                    pulse.toggle()
                }
            }
    }
}

struct ClusterCardView: View {
    let cluster: ClusterViewData

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Cluster \(cluster.id)")
                .font(.headline)
                .foregroundColor(.white)
            Text("Confidence: \(cluster.confidence) • Trend: \(cluster.trend)")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
            Text("Devices: \(cluster.deviceCount) • Stability: \(cluster.stability)")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.black.opacity(0.7))
        .cornerRadius(12)
    }
}

struct CameraPreview: UIViewRepresentable {
    let position: AVCaptureDevice.Position

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = PreviewView(frame: .zero)
        context.coordinator.controller.attachPreview(to: view)
        context.coordinator.controller.start(position: position)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.controller.updateFrame(for: uiView)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.controller.stop()
    }

    final class Coordinator {
        let controller = CameraSessionController()
    }
}

final class CameraSessionController {
    private let session = AVCaptureSession()
    private let previewLayer = AVCaptureVideoPreviewLayer()
    private let sessionQueue = DispatchQueue(label: "pulse.camera.session")
    private var configured = false

    func attachPreview(to view: UIView) {
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.session = session
        view.layer.addSublayer(previewLayer)
        previewLayer.frame = view.bounds
        previewLayer.needsDisplayOnBoundsChange = true
    }

    func updateFrame(for view: UIView) {
        previewLayer.frame = view.bounds
    }

    func start(position: AVCaptureDevice.Position) {
        sessionQueue.async {
            if !self.configured {
                self.configureSession(position: position)
            }
            if !self.session.isRunning {
                self.session.startRunning()
            }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    private func configureSession(position: AVCaptureDevice.Position) {
        session.beginConfiguration()
        session.sessionPreset = .high
        session.inputs.forEach { session.removeInput($0) }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)
        if let connection = previewLayer.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        session.commitConfiguration()
        configured = true
    }
}

final class PreviewView: UIView {
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.sublayers?.forEach { layer in
            layer.frame = bounds
        }
    }
}
