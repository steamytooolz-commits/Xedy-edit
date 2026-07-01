# Dockerfile for Alpine-based Android & Native Builds
FROM alpine:3.21

# Install build dependencies, JDK, and Android SDK command line tools
RUN apk add --no-cache \
    openjdk17 \
    bash \
    curl \
    git \
    unzip \
    libstdc++ \
    gcompat \
    clang \
    g++ \
    make \
    cmake

# Set Environment Variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Download and set up Android command line tools
WORKDIR /opt
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -o sdk-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip sdk-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm sdk-tools.zip

# Accept licenses and install platform tools / build tools
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"

# Set workspace directory
WORKDIR /home/alpine/workspace
COPY . /home/alpine/workspace

# Set gradle user home inside workspace to cache dependencies
ENV GRADLE_USER_HOME=/home/alpine/workspace/.gradle

# Command to build the project
CMD ["./gradlew", "assembleDebug"]
