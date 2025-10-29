// OpenGL ES 2.0 fragment shader for Voice Orb
precision mediump float;

uniform float uTime;
uniform float uAmplitude;
uniform float uCentroid;
uniform float uMode; // 0 idle, 1 listening, 2 thinking, 3 speaking
uniform vec3 uColorA; // Indigo
uniform vec3 uColorB; // Fuchsia

varying vec3 vNormal;
varying float vNoise;
varying float vAmp;
varying float vCentroid;

void main(){
    // Base gradient color
    float glow = clamp((vNoise * 0.5 + 0.5) * (0.6 + vAmp * 0.8), 0.0, 1.0);
    vec3 base = mix(uColorA, uColorB, clamp(vCentroid, 0.0, 1.0));

    // Mode overlay tint
    vec3 modeTint = vec3(0.0);
    if (uMode < 0.5) { // idle
        modeTint = vec3(0.05, 0.05, 0.08);
    } else if (uMode < 1.5) { // listening
        modeTint = vec3(0.0, 0.08, 0.2);
    } else if (uMode < 2.5) { // thinking
        modeTint = vec3(0.08, 0.0, 0.15);
    } else { // speaking
        modeTint = vec3(0.15, 0.0, 0.15);
    }

    float rim = pow(1.0 - max(dot(normalize(vNormal), vec3(0.0,0.0,1.0)), 0.0), 2.0);
    float emission = glow * 0.7 + rim * 0.3;

    vec3 color = base * (0.5 + glow * 0.5) + modeTint * 0.3 + emission * 0.2;
    gl_FragColor = vec4(color, 0.95);
}
